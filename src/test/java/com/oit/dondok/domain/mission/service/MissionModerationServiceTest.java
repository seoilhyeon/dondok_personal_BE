package com.oit.dondok.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.dto.response.MissionModerationResponse;
import com.oit.dondok.domain.mission.entity.CertificationStatus;
import com.oit.dondok.domain.mission.entity.ExifRisk;
import com.oit.dondok.domain.mission.entity.MissionLog;
import com.oit.dondok.domain.mission.entity.ModerationDecisionType;
import com.oit.dondok.domain.mission.entity.ModerationHistory;
import com.oit.dondok.domain.mission.exception.MissionErrorCode;
import com.oit.dondok.domain.mission.repository.MissionLogQueryRepository;
import com.oit.dondok.domain.mission.repository.ModerationHistoryRepository;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import com.oit.dondok.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
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
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 8, 11, 0);

  @Mock private MissionLogQueryRepository missionLogQueryRepository;
  @Mock private ModerationHistoryRepository moderationHistoryRepository;
  @Mock private SettlementRepository settlementRepository;

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
            objectMapper);
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

  // 방장이 아닌 사용자의 승인 요청은 권한 오류가 나고 상태와 이력을 변경하지 않는다.
  @Test
  void rejectWhenRequesterIsNotHost() {
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

  // 이미 승인된 인증 로그는 중복 승인할 수 없고 이력을 추가하지 않는다.
  @Test
  void rejectWhenMissionLogAlreadyApproved() {
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

  // 정산이 시작된 크루의 인증은 정산 결과와의 불일치를 막기 위해 승인하지 않는다.
  @Test
  void rejectWhenSettlementAlreadyStarted() {
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

    assertThatThrownBy(() -> missionModerationService.approve(HOST_UUID, MISSION_LOG_ID))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.MISSION_LOG_NOT_FOUND);

    verify(moderationHistoryRepository, never()).save(any());
  }

  private void givenMissionLogFound(MissionLog missionLog) {
    given(missionLogQueryRepository.findByIdWithCrewForModeration(MISSION_LOG_ID))
        .willReturn(Optional.of(missionLog));
  }

  private void givenNoSettlementStarted() {
    given(settlementRepository.findByCrewId(CREW_ID)).willReturn(Optional.empty());
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
}
