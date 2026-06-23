package com.oit.dondok.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.dto.response.MissionModerationResponse;
import com.oit.dondok.domain.mission.entity.CertificationStatus;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.ExifRisk;
import com.oit.dondok.domain.mission.entity.MissionLog;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.entity.ModerationDecisionType;
import com.oit.dondok.domain.mission.entity.ModerationHistory;
import com.oit.dondok.domain.mission.entity.RejectReasonCode;
import com.oit.dondok.domain.mission.exception.MissionErrorCode;
import com.oit.dondok.domain.mission.repository.MissionLogQueryRepository;
import com.oit.dondok.domain.mission.repository.MissionRuleRepository;
import com.oit.dondok.domain.mission.repository.ModerationHistoryRepository;
import com.oit.dondok.domain.notification.port.NotificationPayload;
import com.oit.dondok.domain.notification.port.NotificationSender;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import com.oit.dondok.global.exception.CustomException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MissionModerationServiceTest {

  private static final UUID HOST_UUID = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
  private static final UUID OTHER_UUID = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c902");
  private static final Long HOST_ID = 1L;
  private static final Long MEMBER_ID = 2L;
  private static final Long CREW_ID = 42L;
  private static final Long PARTICIPANT_ID = 101L;
  private static final Long MISSION_LOG_ID = 1001L;
  private static final Long MODERATION_HISTORY_ID = 9001L;
  private static final LocalDateTime NOW = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

  @Mock private MissionLogQueryRepository missionLogQueryRepository;
  @Mock private MissionRuleRepository missionRuleRepository;
  @Mock private ModerationHistoryRepository moderationHistoryRepository;
  @Mock private SettlementRepository settlementRepository;
  @Mock private NotificationSender notificationSender;

  private ObjectMapper objectMapper;
  private MissionModerationService missionModerationService;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    missionModerationService =
        new MissionModerationService(
            moderationHistoryRepository,
            settlementRepository,
            missionLogQueryRepository,
            missionRuleRepository,
            objectMapper,
            notificationSender);
    givenReviewablePeriod();
  }

  // 방장이 검수 대기 인증을 승인하면 MissionLog 상태와 응답, 이력이 함께 갱신된다.
  @Test
  void approvePendingReviewLogByHost() {
    MissionLog missionLog = pendingReviewLog();
    givenMissionLogFound(missionLog);
    givenNoSettlementStarted();
    givenHistorySaveReturnsWithId();

    MissionModerationResponse response =
        missionModerationService.approve(HOST_UUID, MISSION_LOG_ID);

    assertThat(missionLog.getCertificationStatus()).isEqualTo(CertificationStatus.SUCCESS);
    assertThat(missionLog.getDecisionType()).isEqualTo(ModerationDecisionType.MANUAL_APPROVE);
    assertThat(missionLog.getModerator()).isEqualTo(host(missionLog));
    assertThat(missionLog.getModeratorDecidedAt()).isNotNull();
    assertThat(missionLog.getRejectReasonCode()).isNull();
    assertThat(missionLog.getRejectMemo()).isNull();

    assertThat(response.missionLogId()).isEqualTo(MISSION_LOG_ID);
    assertThat(response.crewId()).isEqualTo(CREW_ID);
    assertThat(response.crewParticipantId()).isEqualTo(PARTICIPANT_ID);
    assertThat(response.certificationStatus()).isEqualTo(CertificationStatus.SUCCESS);
    assertThat(response.decisionType()).isEqualTo(ModerationDecisionType.MANUAL_APPROVE);
    assertThat(response.moderationHistoryId()).isEqualTo(MODERATION_HISTORY_ID);

    verify(moderationHistoryRepository).save(any(ModerationHistory.class));
  }

  // 방장이 거절하면 MissionLog는 FAILED/MANUAL_REJECT 상태가 된다.
  @Test
  void rejectPendingReviewLogByHost() {
    MissionLog missionLog = pendingReviewLog();
    givenMissionLogFound(missionLog);
    givenNoSettlementStarted();
    givenHistorySaveReturnsWithId();

    MissionModerationResponse response =
        missionModerationService.reject(
            HOST_UUID, MISSION_LOG_ID, RejectReasonCode.MISSION_MISMATCH, "사진이 미션과 다릅니다");

    assertThat(missionLog.getCertificationStatus()).isEqualTo(CertificationStatus.FAILED);
    assertThat(missionLog.getDecisionType()).isEqualTo(ModerationDecisionType.MANUAL_REJECT);
    assertThat(missionLog.getRejectReasonCode()).isEqualTo(RejectReasonCode.MISSION_MISMATCH);
    assertThat(missionLog.getRejectMemo()).isEqualTo("사진이 미션과 다릅니다");
    assertThat(missionLog.getModerator()).isEqualTo(host(missionLog));
    assertThat(missionLog.getModeratorDecidedAt()).isNotNull();

    assertThat(response.missionLogId()).isEqualTo(MISSION_LOG_ID);
    assertThat(response.certificationStatus()).isEqualTo(CertificationStatus.FAILED);
    assertThat(response.decisionType()).isEqualTo(ModerationDecisionType.MANUAL_REJECT);
    assertThat(response.rejectReasonCode()).isEqualTo(RejectReasonCode.MISSION_MISMATCH);
    assertThat(response.moderationHistoryId()).isEqualTo(MODERATION_HISTORY_ID);

    verify(moderationHistoryRepository).save(any(ModerationHistory.class));
  }

  @Test
  void approveAutoRejectedLogByHost() {
    MissionLog missionLog = pendingReviewLog();
    ReflectionTestUtils.setField(missionLog, "duplicateHash", true);
    missionLog.rejectAutomatically(host(missionLog), NOW.minusMinutes(1));
    givenMissionLogFound(missionLog);
    givenNoSettlementStarted();
    givenHistorySaveReturnsWithId();

    MissionModerationResponse response =
        missionModerationService.approve(HOST_UUID, MISSION_LOG_ID);

    assertThat(missionLog.getCertificationStatus()).isEqualTo(CertificationStatus.SUCCESS);
    assertThat(missionLog.getDecisionType()).isEqualTo(ModerationDecisionType.MANUAL_APPROVE);
    assertThat(missionLog.getRejectReasonCode()).isNull();
    assertThat(missionLog.getRejectMemo()).isNull();
    assertThat(response.certificationStatus()).isEqualTo(CertificationStatus.SUCCESS);
    assertThat(response.decisionType()).isEqualTo(ModerationDecisionType.MANUAL_APPROVE);
    verify(moderationHistoryRepository).save(any(ModerationHistory.class));
  }

  @Test
  void rejectAutoApprovedLogByHost() {
    MissionLog missionLog = pendingReviewLog();
    missionLog.approveAutomatically(host(missionLog), NOW.minusMinutes(1));
    givenMissionLogFound(missionLog);
    givenNoSettlementStarted();
    givenHistorySaveReturnsWithId();

    MissionModerationResponse response =
        missionModerationService.reject(
            HOST_UUID, MISSION_LOG_ID, RejectReasonCode.MISSION_MISMATCH, "not matched");

    assertThat(missionLog.getCertificationStatus()).isEqualTo(CertificationStatus.FAILED);
    assertThat(missionLog.getDecisionType()).isEqualTo(ModerationDecisionType.MANUAL_REJECT);
    assertThat(missionLog.getRejectReasonCode()).isEqualTo(RejectReasonCode.MISSION_MISMATCH);
    assertThat(missionLog.getRejectMemo()).isEqualTo("not matched");
    assertThat(response.certificationStatus()).isEqualTo(CertificationStatus.FAILED);
    assertThat(response.decisionType()).isEqualTo(ModerationDecisionType.MANUAL_REJECT);
    verify(moderationHistoryRepository).save(any(ModerationHistory.class));
  }

  // OTHER 거절 사유는 내부 확인을 위한 메모가 반드시 필요하다.
  @Test
  void rejectWhenOtherReasonHasNoMemo() {
    MissionLog missionLog = pendingReviewLog();
    givenMissionLogFound(missionLog);
    givenNoSettlementStarted();

    assertThatThrownBy(
            () ->
                missionModerationService.reject(
                    HOST_UUID, MISSION_LOG_ID, RejectReasonCode.OTHER, ""))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.REJECT_MEMO_REQUIRED);

    verify(moderationHistoryRepository, never()).save(any());
  }

  // 거절 메모는 DB 컬럼 길이에 맞춰 50자 이하로 제한한다.
  @Test
  void rejectWhenMemoIsTooLong() {
    MissionLog missionLog = pendingReviewLog();
    givenMissionLogFound(missionLog);
    givenNoSettlementStarted();
    String longMemo = "a".repeat(51);

    assertThatThrownBy(
            () ->
                missionModerationService.reject(
                    HOST_UUID, MISSION_LOG_ID, RejectReasonCode.MISSION_MISMATCH, longMemo))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.REJECT_MEMO_TOO_LONG);

    verify(moderationHistoryRepository, never()).save(any());
  }

  // 거절 이력에는 전후 상태와 거절 사유가 함께 저장된다.
  @Test
  void saveBeforeAndAfterStateSnapshotsWhenRejecting() throws Exception {
    MissionLog missionLog = pendingReviewLog();
    givenMissionLogFound(missionLog);
    givenNoSettlementStarted();
    givenHistorySaveReturnsWithId();
    ArgumentCaptor<ModerationHistory> captor = ArgumentCaptor.forClass(ModerationHistory.class);

    missionModerationService.reject(
        HOST_UUID, MISSION_LOG_ID, RejectReasonCode.MISSION_MISMATCH, "사진이 미션과 다릅니다");

    verify(moderationHistoryRepository).save(captor.capture());
    ModerationHistory history = captor.getValue();
    JsonNode beforeState = objectMapper.readTree(history.getBeforeState());
    JsonNode afterState = objectMapper.readTree(history.getAfterState());

    assertThat(beforeState.get("certification_status").asText()).isEqualTo("PENDING_REVIEW");
    assertThat(beforeState.has("exif_risk")).isFalse();
    assertThat(beforeState.has("duplicate_hash")).isFalse();
    assertThat(beforeState.get("decision_type").isNull()).isTrue();
    assertThat(afterState.get("certification_status").asText()).isEqualTo("FAILED");
    assertThat(afterState.has("failure_reason")).isFalse();
    assertThat(afterState.has("exif_risk")).isFalse();
    assertThat(afterState.has("duplicate_hash")).isFalse();
    assertThat(afterState.get("decision_type").asText()).isEqualTo("MANUAL_REJECT");
    assertThat(afterState.get("reject_reason_code").asText()).isEqualTo("MISSION_MISMATCH");
    assertThat(afterState.get("reject_memo").asText()).isEqualTo("사진이 미션과 다릅니다");
    assertThat(history.getDecisionType()).isEqualTo(ModerationDecisionType.MANUAL_REJECT);
    assertThat(history.getRejectReasonCode()).isEqualTo(RejectReasonCode.MISSION_MISMATCH);
    assertThat(history.getRejectMemo()).isEqualTo("사진이 미션과 다릅니다");
  }

  @Test
  void approveWhenRequesterIsNotHost() {
    MissionLog missionLog = pendingReviewLog();
    givenMissionLogFound(missionLog);

    assertThatThrownBy(() -> missionModerationService.approve(OTHER_UUID, MISSION_LOG_ID))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.FORBIDDEN_NOT_HOST);

    assertThat(missionLog.getCertificationStatus()).isEqualTo(CertificationStatus.PENDING_REVIEW);
    verify(moderationHistoryRepository, never()).save(any());
    verify(settlementRepository, never()).findByCrewId(any());
  }

  // 존재하지 않는 인증 로그는 승인할 수 없다.
  @Test
  void approveWhenMissionLogNotFound() {
    given(missionLogQueryRepository.findByIdWithCrewForModeration(MISSION_LOG_ID))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> missionModerationService.approve(HOST_UUID, MISSION_LOG_ID))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.MISSION_LOG_NOT_FOUND);

    verify(moderationHistoryRepository, never()).save(any());
  }

  // 이미 결정된 인증 로그는 다시 승인할 수 없다.
  @Test
  void approveWhenMissionLogAlreadyDecided() {
    MissionLog missionLog = pendingReviewLog();
    missionLog.approveManually(host(missionLog), NOW);
    givenMissionLogFound(missionLog);

    assertThatThrownBy(() -> missionModerationService.approve(HOST_UUID, MISSION_LOG_ID))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.MISSION_LOG_NOT_REVIEWABLE);

    verify(settlementRepository, never()).findByCrewId(any());
    verify(moderationHistoryRepository, never()).save(any());
  }

  // 정산이 시작된 크루의 인증은 승인할 수 없다.
  @Test
  void approveWhenSettlementAlreadyStarted() {
    MissionLog missionLog = pendingReviewLog();
    givenMissionLogFound(missionLog);
    given(settlementRepository.findByCrewId(CREW_ID)).willReturn(Optional.of(mockSettlement()));

    assertThatThrownBy(() -> missionModerationService.approve(HOST_UUID, MISSION_LOG_ID))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.SETTLEMENT_INPUT_FROZEN);

    assertThat(missionLog.getCertificationStatus()).isEqualTo(CertificationStatus.PENDING_REVIEW);
    verify(moderationHistoryRepository, never()).save(any());
  }

  @Test
  void approveWhenCrewIsNotActive() {
    MissionLog missionLog = pendingReviewLog();
    ReflectionTestUtils.setField(
        missionLog.getCrewParticipant().getCrew(), "status", CrewStatus.CLOSED);
    givenMissionLogFound(missionLog);

    assertThatThrownBy(() -> missionModerationService.approve(HOST_UUID, MISSION_LOG_ID))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.MISSION_LOG_NOT_REVIEWABLE);

    verify(settlementRepository, never()).findByCrewId(any());
    verify(moderationHistoryRepository, never()).save(any());
  }

  @Test
  void approveWhenParticipantIsNotLocked() {
    MissionLog missionLog = pendingReviewLog();
    ReflectionTestUtils.setField(
        missionLog.getCrewParticipant(), "status", CrewParticipantStatus.CANCELLED);
    givenMissionLogFound(missionLog);

    assertThatThrownBy(() -> missionModerationService.approve(HOST_UUID, MISSION_LOG_ID))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.MISSION_LOG_NOT_REVIEWABLE);

    verify(settlementRepository, never()).findByCrewId(any());
    verify(moderationHistoryRepository, never()).save(any());
  }

  @Test
  void approveWhenReviewPeriodExpired() {
    MissionLog missionLog = pendingReviewLog();
    ReflectionTestUtils.setField(missionLog, "serverTime", NOW.minusDays(10));
    givenMissionLogFound(missionLog);

    assertThatThrownBy(() -> missionModerationService.approve(HOST_UUID, MISSION_LOG_ID))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.MISSION_LOG_NOT_REVIEWABLE);

    verify(settlementRepository, never()).findByCrewId(any());
    verify(moderationHistoryRepository, never()).save(any());
  }

  @Test
  void approveAutoDecisionWithinGracePeriod() {
    MissionLog missionLog = pendingReviewLog();
    LocalDateTime serverTime = NOW.minusDays(1);
    ReflectionTestUtils.setField(missionLog, "serverTime", serverTime);
    missionLog.approveAutomatically(
        host(missionLog), DailySettlementType.B.autoCertificationAt(serverTime.toLocalDate()));
    givenMissionLogFound(missionLog);
    givenNoSettlementStarted();
    givenHistorySaveReturnsWithId();

    missionModerationService.approve(HOST_UUID, MISSION_LOG_ID);

    assertThat(missionLog.getCertificationStatus()).isEqualTo(CertificationStatus.SUCCESS);
    assertThat(missionLog.getDecisionType()).isEqualTo(ModerationDecisionType.MANUAL_APPROVE);
    verify(moderationHistoryRepository).save(any(ModerationHistory.class));
  }

  // 검수 기간이 만료된 인증은 거절할 수 없다.
  @Test
  void rejectWhenReviewPeriodExpired() {
    MissionLog missionLog = pendingReviewLog();
    ReflectionTestUtils.setField(missionLog, "serverTime", NOW.minusDays(10));
    givenMissionLogFound(missionLog);

    assertThatThrownBy(
            () ->
                missionModerationService.reject(
                    HOST_UUID, MISSION_LOG_ID, RejectReasonCode.MISSION_MISMATCH, null))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.MISSION_LOG_NOT_REVIEWABLE);

    verify(settlementRepository, never()).findByCrewId(any());
    verify(moderationHistoryRepository, never()).save(any());
  }

  // 방장이 아닌 사용자는 인증을 거절할 수 없고 이력도 저장되지 않는다.
  @Test
  void rejectWhenRequesterIsNotHost() {
    MissionLog missionLog = pendingReviewLog();
    givenMissionLogFound(missionLog);

    assertThatThrownBy(
            () ->
                missionModerationService.reject(
                    OTHER_UUID, MISSION_LOG_ID, RejectReasonCode.MISSION_MISMATCH, null))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.FORBIDDEN_NOT_HOST);

    assertThat(missionLog.getCertificationStatus()).isEqualTo(CertificationStatus.PENDING_REVIEW);
    verify(moderationHistoryRepository, never()).save(any());
    verify(settlementRepository, never()).findByCrewId(any());
  }

  // 이미 승인된 인증 로그는 거절할 수 없고 이력을 추가하지 않는다.
  @Test
  void rejectWhenMissionLogAlreadyApproved() {
    MissionLog missionLog = pendingReviewLog();
    missionLog.approveManually(host(missionLog), NOW);
    givenMissionLogFound(missionLog);

    assertThatThrownBy(
            () ->
                missionModerationService.reject(
                    HOST_UUID, MISSION_LOG_ID, RejectReasonCode.MISSION_MISMATCH, null))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.MISSION_LOG_NOT_REVIEWABLE);

    verify(settlementRepository, never()).findByCrewId(any());
    verify(moderationHistoryRepository, never()).save(any());
  }

  // 정산이 시작된 크루의 인증은 정산 결과와의 불일치를 막기 위해 거절하지 않는다.
  @Test
  void rejectWhenSettlementAlreadyStarted() {
    MissionLog missionLog = pendingReviewLog();
    givenMissionLogFound(missionLog);
    given(settlementRepository.findByCrewId(CREW_ID)).willReturn(Optional.of(mockSettlement()));

    assertThatThrownBy(
            () ->
                missionModerationService.reject(
                    HOST_UUID, MISSION_LOG_ID, RejectReasonCode.MISSION_MISMATCH, null))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.SETTLEMENT_INPUT_FROZEN);

    assertThat(missionLog.getCertificationStatus()).isEqualTo(CertificationStatus.PENDING_REVIEW);
    verify(moderationHistoryRepository, never()).save(any());
  }

  // 승인 이력에는 승인 전 PENDING 상태와 승인 후 SUCCESS 상태 스냅샷이 저장된다.
  @Test
  void saveBeforeAndAfterStateSnapshots() throws Exception {
    MissionLog missionLog = pendingReviewLog();
    givenMissionLogFound(missionLog);
    givenNoSettlementStarted();
    givenHistorySaveReturnsWithId();
    ArgumentCaptor<ModerationHistory> captor = ArgumentCaptor.forClass(ModerationHistory.class);

    missionModerationService.approve(HOST_UUID, MISSION_LOG_ID);

    verify(moderationHistoryRepository).save(captor.capture());
    ModerationHistory history = captor.getValue();
    JsonNode beforeState = objectMapper.readTree(history.getBeforeState());
    JsonNode afterState = objectMapper.readTree(history.getAfterState());

    assertThat(beforeState.get("certification_status").asText()).isEqualTo("PENDING_REVIEW");
    assertThat(beforeState.get("decision_type").isNull()).isTrue();
    assertThat(beforeState.get("moderator_id").isNull()).isTrue();
    assertThat(afterState.get("certification_status").asText()).isEqualTo("SUCCESS");
    assertThat(afterState.get("decision_type").asText()).isEqualTo("MANUAL_APPROVE");
    assertThat(afterState.get("moderator_id").asLong()).isEqualTo(HOST_ID);
    assertThat(afterState.get("moderator_decided_at").isNull()).isFalse();
    assertThat(history.getModerator()).isEqualTo(host(missionLog));
    assertThat(history.getChangedAt()).isEqualTo(missionLog.getModeratorDecidedAt());
  }

  // 존재하지 않는 인증 로그 ID는 미션 로그 없음 오류로 응답한다.
  @Test
  void rejectWhenMissionLogNotFound() {
    given(missionLogQueryRepository.findByIdWithCrewForModeration(MISSION_LOG_ID))
        .willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                missionModerationService.reject(
                    HOST_UUID, MISSION_LOG_ID, RejectReasonCode.MISSION_MISMATCH, null))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.MISSION_LOG_NOT_FOUND);

    verify(moderationHistoryRepository, never()).save(any());
  }

  // 승인 완료 시 신청자에게 승인 결과 알림이 발송된다.
  @Test
  void approveNotifiesSubmitterWithSuccessMessage() {
    MissionLog missionLog = pendingReviewLog();
    givenMissionLogFound(missionLog);
    givenNoSettlementStarted();
    givenHistorySaveReturnsWithId();

    missionModerationService.approve(HOST_UUID, MISSION_LOG_ID);

    ArgumentCaptor<NotificationPayload> captor = ArgumentCaptor.forClass(NotificationPayload.class);
    then(notificationSender).should(times(1)).send(any(Member.class), captor.capture());
    assertThat(captor.getValue().eventType()).isEqualTo("MISSION_LOG_VERIFICATION_RESULT");
    assertThat(captor.getValue().displayText()).isEqualTo("미션 인증이 승인되었습니다.");
  }

  // 거절 완료 시 신청자에게 거절 결과 알림이 발송된다.
  @Test
  void rejectNotifiesSubmitterWithFailureMessage() {
    MissionLog missionLog = pendingReviewLog();
    givenMissionLogFound(missionLog);
    givenNoSettlementStarted();
    givenHistorySaveReturnsWithId();

    missionModerationService.reject(
        HOST_UUID, MISSION_LOG_ID, RejectReasonCode.MISSION_MISMATCH, "사진이 다릅니다");

    ArgumentCaptor<NotificationPayload> captor = ArgumentCaptor.forClass(NotificationPayload.class);
    then(notificationSender).should(times(1)).send(any(Member.class), captor.capture());
    assertThat(captor.getValue().eventType()).isEqualTo("MISSION_LOG_VERIFICATION_RESULT");
    assertThat(captor.getValue().displayText()).isEqualTo("미션 인증이 거절되었습니다.");
  }

  private void givenMissionLogFound(MissionLog missionLog) {
    given(missionLogQueryRepository.findByIdWithCrewForModeration(MISSION_LOG_ID))
        .willReturn(Optional.of(missionLog));
  }

  private void givenNoSettlementStarted() {
    given(settlementRepository.findByCrewId(CREW_ID)).willReturn(Optional.empty());
  }

  private void givenReviewablePeriod() {
    MissionRule missionRule = Mockito.mock(MissionRule.class);
    // Type B: autoCertificationAt = 내일 자정 → NOW가 언제든 항상 미래값이므로 시각 의존 없음
    Mockito.lenient().when(missionRule.getDailySettlementType()).thenReturn(DailySettlementType.B);
    Mockito.lenient()
        .when(missionRuleRepository.findByCrewId(CREW_ID))
        .thenReturn(Optional.of(missionRule));
  }

  private void givenHistorySaveReturnsWithId() {
    given(moderationHistoryRepository.save(any(ModerationHistory.class)))
        .willAnswer(
            invocation -> {
              ModerationHistory history = invocation.getArgument(0);
              ReflectionTestUtils.setField(history, "id", MODERATION_HISTORY_ID);
              return history;
            });
  }

  private MissionLog pendingReviewLog() {
    Member host = member(HOST_ID, HOST_UUID, "host@example.com", "host");
    Member participantMember = member(MEMBER_ID, OTHER_UUID, "member@example.com", "member");
    Crew crew =
        Crew.create(
            host,
            "morning crew",
            "daily mission crew",
            null,
            "HEALTH",
            "{}",
            HostPolicyVersion.HOST_POLICY_V1,
            NOW,
            10_000L,
            2,
            10,
            NOW.plusDays(1),
            NOW.plusDays(2),
            NOW.plusDays(30));
    ReflectionTestUtils.setField(crew, "id", CREW_ID);
    ReflectionTestUtils.setField(crew, "status", CrewStatus.ACTIVE);

    CrewParticipant participant = CrewParticipant.create(crew, participantMember, 10_000L, NOW);
    ReflectionTestUtils.setField(participant, "id", PARTICIPANT_ID);

    MissionLog missionLog =
        MissionLog.createPendingReview(
            participant,
            "mission/42/101/image.jpg",
            "daily mission done",
            "9b74c9897bac770ffc029102a200c5de8c0e9e5b9d3c9c7e5f4f5c1a2b3c4d5e",
            NOW,
            ExifRisk.NORMAL,
            false,
            NOW);
    ReflectionTestUtils.setField(missionLog, "id", MISSION_LOG_ID);
    return missionLog;
  }

  private Member member(Long id, UUID uuid, String email, String nickname) {
    Member member = Member.create(email, "password-hash", nickname);
    ReflectionTestUtils.setField(member, "id", id);
    ReflectionTestUtils.setField(member, "uuid", uuid);
    return member;
  }

  private Member host(MissionLog missionLog) {
    return missionLog.getCrewParticipant().getCrew().getHostMember();
  }

  private Settlement mockSettlement() {
    return org.mockito.Mockito.mock(Settlement.class);
  }

  // 방장이 수동 승인한 인증을 검토 대기로 되돌리면 상태가 PENDING_REVIEW로 초기화된다.
  @Test
  void revertManuallyApprovedLogRestoresPendingReview() {
    MissionLog missionLog = pendingReviewLog();
    missionLog.approveManually(host(missionLog), NOW.minusMinutes(5));
    givenMissionLogFound(missionLog);
    givenNoSettlementStarted();
    givenHistorySaveReturnsWithId();

    MissionModerationResponse response = missionModerationService.revert(HOST_UUID, MISSION_LOG_ID);

    assertThat(missionLog.getCertificationStatus()).isEqualTo(CertificationStatus.PENDING_REVIEW);
    assertThat(missionLog.getDecisionType()).isNull();
    assertThat(missionLog.getModerator()).isNull();
    assertThat(missionLog.getModeratorDecidedAt()).isNull();
    assertThat(missionLog.getRejectReasonCode()).isNull();
    assertThat(missionLog.getRejectMemo()).isNull();

    assertThat(response.missionLogId()).isEqualTo(MISSION_LOG_ID);
    assertThat(response.certificationStatus()).isEqualTo(CertificationStatus.PENDING_REVIEW);
    assertThat(response.decisionType()).isNull();
    assertThat(response.rejectReasonCode()).isNull();
    assertThat(response.moderationHistoryId()).isEqualTo(MODERATION_HISTORY_ID);

    verify(moderationHistoryRepository).save(any(ModerationHistory.class));
  }

  // 방장이 수동 거절한 인증도 검토 대기로 되돌릴 수 있다.
  @Test
  void revertManuallyRejectedLogRestoresPendingReview() {
    MissionLog missionLog = pendingReviewLog();
    missionLog.rejectManually(
        host(missionLog), RejectReasonCode.MISSION_MISMATCH, "사진 불일치", NOW.minusMinutes(5));
    givenMissionLogFound(missionLog);
    givenNoSettlementStarted();
    givenHistorySaveReturnsWithId();

    MissionModerationResponse response = missionModerationService.revert(HOST_UUID, MISSION_LOG_ID);

    assertThat(missionLog.getCertificationStatus()).isEqualTo(CertificationStatus.PENDING_REVIEW);
    assertThat(missionLog.getDecisionType()).isNull();
    assertThat(missionLog.getRejectReasonCode()).isNull();
    assertThat(missionLog.getRejectMemo()).isNull();
    assertThat(response.certificationStatus()).isEqualTo(CertificationStatus.PENDING_REVIEW);
    assertThat(response.decisionType()).isNull();
    verify(moderationHistoryRepository).save(any(ModerationHistory.class));
  }

  // PENDING_REVIEW 상태 인증은 되돌릴 수 없다.
  @Test
  void revertWhenLogIsAlreadyPendingReview() {
    MissionLog missionLog = pendingReviewLog();
    givenMissionLogFound(missionLog);

    assertThatThrownBy(() -> missionModerationService.revert(HOST_UUID, MISSION_LOG_ID))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.MISSION_LOG_DECISION_NOT_REVERSIBLE);

    verify(moderationHistoryRepository, never()).save(any());
    verify(settlementRepository, never()).findByCrewId(any());
  }

  // 시스템 자동 승인 인증은 방장이 approve/reject로 직접 결정하므로 되돌리기 대상이 아니다.
  @Test
  void revertWhenLogIsAutoApproved() {
    MissionLog missionLog = pendingReviewLog();
    missionLog.approveAutomatically(host(missionLog), NOW.minusMinutes(5));
    givenMissionLogFound(missionLog);

    assertThatThrownBy(() -> missionModerationService.revert(HOST_UUID, MISSION_LOG_ID))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.MISSION_LOG_DECISION_NOT_REVERSIBLE);

    verify(moderationHistoryRepository, never()).save(any());
  }

  // 시스템 자동 거절 인증도 되돌리기 대상이 아니다.
  @Test
  void revertWhenLogIsAutoRejected() {
    MissionLog missionLog = pendingReviewLog();
    ReflectionTestUtils.setField(missionLog, "duplicateHash", true);
    missionLog.rejectAutomatically(host(missionLog), NOW.minusMinutes(5));
    givenMissionLogFound(missionLog);

    assertThatThrownBy(() -> missionModerationService.revert(HOST_UUID, MISSION_LOG_ID))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.MISSION_LOG_DECISION_NOT_REVERSIBLE);

    verify(moderationHistoryRepository, never()).save(any());
  }

  // 방장이 아닌 사용자는 되돌리기를 수행할 수 없다.
  @Test
  void revertWhenRequesterIsNotHost() {
    MissionLog missionLog = pendingReviewLog();
    missionLog.approveManually(host(missionLog), NOW.minusMinutes(5));
    givenMissionLogFound(missionLog);

    assertThatThrownBy(() -> missionModerationService.revert(OTHER_UUID, MISSION_LOG_ID))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.FORBIDDEN_NOT_HOST);

    verify(moderationHistoryRepository, never()).save(any());
  }

  // 정산이 시작된 크루의 인증은 되돌릴 수 없다.
  @Test
  void revertWhenSettlementAlreadyStarted() {
    MissionLog missionLog = pendingReviewLog();
    missionLog.approveManually(host(missionLog), NOW.minusMinutes(5));
    givenMissionLogFound(missionLog);
    given(settlementRepository.findByCrewId(CREW_ID)).willReturn(Optional.of(mockSettlement()));

    assertThatThrownBy(() -> missionModerationService.revert(HOST_UUID, MISSION_LOG_ID))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.SETTLEMENT_INPUT_FROZEN);

    verify(moderationHistoryRepository, never()).save(any());
  }

  // 검수 기간이 만료된 인증은 되돌릴 수 없다.
  @Test
  void revertWhenReviewPeriodExpired() {
    MissionLog missionLog = pendingReviewLog();
    missionLog.approveManually(host(missionLog), NOW.minusDays(10));
    ReflectionTestUtils.setField(missionLog, "serverTime", NOW.minusDays(10));
    givenMissionLogFound(missionLog);

    assertThatThrownBy(() -> missionModerationService.revert(HOST_UUID, MISSION_LOG_ID))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.MISSION_LOG_NOT_REVIEWABLE);

    verify(moderationHistoryRepository, never()).save(any());
  }

  // 되돌리기 이력에는 결정 전 상태, 되돌린 후 PENDING_REVIEW 스냅샷과 MANUAL_REVERT 결정 유형이 저장된다.
  @Test
  void revertSavesSnapshotWithManualRevertDecisionType() throws Exception {
    MissionLog missionLog = pendingReviewLog();
    missionLog.approveManually(host(missionLog), NOW.minusMinutes(5));
    givenMissionLogFound(missionLog);
    givenNoSettlementStarted();
    givenHistorySaveReturnsWithId();
    ArgumentCaptor<ModerationHistory> captor = ArgumentCaptor.forClass(ModerationHistory.class);

    missionModerationService.revert(HOST_UUID, MISSION_LOG_ID);

    verify(moderationHistoryRepository).save(captor.capture());
    ModerationHistory history = captor.getValue();
    JsonNode beforeState = objectMapper.readTree(history.getBeforeState());
    JsonNode afterState = objectMapper.readTree(history.getAfterState());

    assertThat(beforeState.get("certification_status").asText()).isEqualTo("SUCCESS");
    assertThat(beforeState.get("decision_type").asText()).isEqualTo("MANUAL_APPROVE");
    assertThat(beforeState.get("moderator_id").asLong()).isEqualTo(HOST_ID);

    assertThat(afterState.get("certification_status").asText()).isEqualTo("PENDING_REVIEW");
    assertThat(afterState.get("decision_type").isNull()).isTrue();
    assertThat(afterState.get("moderator_id").isNull()).isTrue();
    assertThat(afterState.get("reject_reason_code").isNull()).isTrue();
    assertThat(afterState.get("reject_memo").isNull()).isTrue();

    assertThat(history.getDecisionType()).isEqualTo(ModerationDecisionType.MANUAL_REVERT);
    assertThat(history.getModerator()).isEqualTo(host(missionLog));
  }

  // 존재하지 않는 인증 로그 ID는 미션 로그 없음 오류로 응답한다.
  @Test
  void revertWhenMissionLogNotFound() {
    given(missionLogQueryRepository.findByIdWithCrewForModeration(MISSION_LOG_ID))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> missionModerationService.revert(HOST_UUID, MISSION_LOG_ID))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.MISSION_LOG_NOT_FOUND);

    verify(moderationHistoryRepository, never()).save(any());
  }
}
