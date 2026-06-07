package com.oit.dondok.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.image.port.ImageObjectKeyPolicy;
import com.oit.dondok.domain.mission.dto.request.MissionLogCreateRequest;
import com.oit.dondok.domain.mission.dto.response.ImageVerifyResponse;
import com.oit.dondok.domain.mission.dto.response.MissionLogCreateResponse;
import com.oit.dondok.domain.mission.entity.CertificationStatus;
import com.oit.dondok.domain.mission.entity.ExifRisk;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.mission.entity.MissionLog;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.exception.MissionErrorCode;
import com.oit.dondok.domain.mission.port.ImageProcessingPort;
import com.oit.dondok.domain.mission.repository.MissionLogRepository;
import com.oit.dondok.domain.mission.repository.MissionRuleRepository;
import com.oit.dondok.domain.mission.repository.MissionScheduleDayRepository;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class MissionLogServiceTest {

  private static final UUID MEMBER_UUID = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
  private static final Long CREW_ID = 42L;
  private static final Long PARTICIPANT_ID = 101L;
  private static final Long RULE_ID = 7L;
  private static final String S3_KEY = "mission/42/101/3f2504e0-4f89-41d3-9a0c-0305e82c3301";
  private static final String CAPTION = "오늘도 미션 완료";
  private static final String HASH =
      "9b74c9897bac770ffc029102a200c5de8c0e9e5b9d3c9c7e5f4f5c1a2b3c4d5e";
  private static final ZoneOffset KST = ZoneOffset.ofHours(9);
  private static final OffsetDateTime TAKEN_AT = OffsetDateTime.of(2026, 6, 6, 8, 0, 0, 0, KST);
  // server_time(now) 기준 미션 기간을 충분히 감싸는 경계값 (period 검증을 통과시키기 위함).
  private static final LocalDateTime FAR_PAST = LocalDateTime.of(2000, 1, 1, 0, 0);
  private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(2100, 1, 1, 0, 0);

  @Mock private CrewParticipantRepository crewParticipantRepository;
  @Mock private MissionRuleRepository missionRuleRepository;
  @Mock private MissionScheduleDayRepository missionScheduleDayRepository;
  @Mock private MissionLogRepository missionLogRepository;
  @Mock private MissionImageService missionImageService;
  @Mock private ImageObjectKeyPolicy imageObjectKeyPolicy;
  @Mock private ImageProcessingPort imageProcessingPort;

  @InjectMocks private MissionLogService missionLogService;

  // participant가 (crewId, memberUuid)로 조회되지 않으면 PARTICIPANT_NOT_FOUND.
  @Test
  void throwsWhenParticipantNotFound() {
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, MEMBER_UUID))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> missionLogService.createMissionLog(MEMBER_UUID, request()))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.PARTICIPANT_NOT_FOUND);
  }

  // 제출 key가 본인 participant 네임스페이스가 아니면 INVALID_INPUT (IDOR/변조 차단).
  @Test
  void throwsWhenSubmittedKeyNotOwnedByParticipant() {
    givenParticipantFound(participant(CrewParticipantStatus.LOCKED));
    givenKeyMatches(false);

    assertThatThrownBy(() -> missionLogService.createMissionLog(MEMBER_UUID, request()))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.INVALID_INPUT);
  }

  // LOCKED가 아닌 참여자는 PARTICIPANT_NOT_ELIGIBLE.
  @ParameterizedTest
  @EnumSource(value = CrewParticipantStatus.class, names = "LOCKED", mode = Mode.EXCLUDE)
  void throwsWhenParticipantNotLocked(CrewParticipantStatus status) {
    givenParticipantFound(participant(status));
    givenKeyMatches(true);

    assertThatThrownBy(() -> missionLogService.createMissionLog(MEMBER_UUID, request()))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.PARTICIPANT_NOT_ELIGIBLE);
  }

  // 미션 시작 전(server_time < start_at) 제출은 MISSION_NOT_STARTED.
  @Test
  void throwsWhenBeforeMissionStart() {
    givenParticipantFound(participant(CrewParticipantStatus.LOCKED, FAR_FUTURE, FAR_FUTURE));
    givenKeyMatches(true);

    assertThatThrownBy(() -> missionLogService.createMissionLog(MEMBER_UUID, request()))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.MISSION_NOT_STARTED);
  }

  // 미션 종료 후(server_time > end_at) 제출은 MISSION_ENDED.
  @Test
  void throwsWhenAfterMissionEnd() {
    givenParticipantFound(participant(CrewParticipantStatus.LOCKED, FAR_PAST, FAR_PAST));
    givenKeyMatches(true);

    assertThatThrownBy(() -> missionLogService.createMissionLog(MEMBER_UUID, request()))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.MISSION_ENDED);
  }

  // 크루의 미션 규칙이 없으면 MISSION_RULE_NOT_FOUND.
  @Test
  void throwsWhenMissionRuleNotFound() {
    givenParticipantFound(participant(CrewParticipantStatus.LOCKED));
    givenKeyMatches(true);
    given(missionRuleRepository.findByCrewId(CREW_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> missionLogService.createMissionLog(MEMBER_UUID, request()))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.MISSION_RULE_NOT_FOUND);
  }

  // SPECIFIC_DAYS 크루에서 오늘이 미션 가능일이 아니면 NOT_MISSION_DAY.
  @Test
  void throwsWhenNotMissionDayForSpecificDays() {
    givenParticipantFound(participant(CrewParticipantStatus.LOCKED));
    givenKeyMatches(true);
    givenMissionRule(MissionFrequencyType.SPECIFIC_DAYS);
    givenMissionDay(false);

    assertThatThrownBy(() -> missionLogService.createMissionLog(MEMBER_UUID, request()))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.NOT_MISSION_DAY);
  }

  // 당일 SUCCESS 로그가 있으면 ALREADY_CERTIFIED_TODAY.
  @Test
  void throwsAlreadyCertifiedWhenSuccessLogExistsToday() {
    givenParticipantFound(participant(CrewParticipantStatus.LOCKED));
    givenKeyMatches(true);
    givenMissionRule(MissionFrequencyType.DAILY);
    givenSuccessLogExists(true);

    assertThatThrownBy(() -> missionLogService.createMissionLog(MEMBER_UUID, request()))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.ALREADY_CERTIFIED_TODAY);
  }

  // 당일 PENDING_REVIEW 로그가 있으면 CERTIFICATION_IN_REVIEW.
  @Test
  void throwsInReviewWhenPendingLogExistsToday() {
    givenParticipantFound(participant(CrewParticipantStatus.LOCKED));
    givenKeyMatches(true);
    givenMissionRule(MissionFrequencyType.DAILY);
    givenSuccessLogExists(false);
    givenPendingLogExists(true);

    assertThatThrownBy(() -> missionLogService.createMissionLog(MEMBER_UUID, request()))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.CERTIFICATION_IN_REVIEW);
  }

  // SUCCESS와 PENDING_REVIEW가 모두 있어도 SUCCESS가 우선(PENDING 조회는 단락되어 호출되지 않음).
  @Test
  void prioritizesAlreadyCertifiedOverInReview() {
    givenParticipantFound(participant(CrewParticipantStatus.LOCKED));
    givenKeyMatches(true);
    givenMissionRule(MissionFrequencyType.DAILY);
    givenSuccessLogExists(true);

    assertThatThrownBy(() -> missionLogService.createMissionLog(MEMBER_UUID, request()))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.ALREADY_CERTIFIED_TODAY);

    verify(missionLogRepository, never())
        .existsByCrewParticipantIdAndCertificationStatusAndServerTimeGreaterThanEqualAndServerTimeLessThan(
            eq(PARTICIPANT_ID), eq(CertificationStatus.PENDING_REVIEW), any(), any());
  }

  // pre-check 거절 시 어떤 부수효과(저장/reEncode)도 발생하지 않는다.
  @Test
  void doesNotPersistOrReEncodeWhenPreCheckRejects() {
    givenParticipantFound(participant(CrewParticipantStatus.PENDING));
    givenKeyMatches(true);

    assertThatThrownBy(() -> missionLogService.createMissionLog(MEMBER_UUID, request()))
        .isInstanceOf(CustomException.class);

    verify(missionLogRepository, never()).save(any());
    verify(imageProcessingPort, never()).reEncode(anyString());
    verify(missionImageService, never()).getImageVerifyResponse(anyLong(), anyString(), any());
  }

  // 정상 제출: PENDING_REVIEW 응답 + 계약 필드 매핑.
  @Test
  void createsPendingReviewLogAndReturnsResponse() {
    givenSubmittableContext(participant(CrewParticipantStatus.LOCKED), MissionFrequencyType.DAILY);
    givenImageVerify(TAKEN_AT, HASH);
    givenSaveReturnsArgument();

    MissionLogCreateResponse response = missionLogService.createMissionLog(MEMBER_UUID, request());

    assertThat(response.certificationStatus()).isEqualTo(CertificationStatus.PENDING_REVIEW);
    assertThat(response.crewId()).isEqualTo(CREW_ID);
    assertThat(response.crewParticipantId()).isEqualTo(PARTICIPANT_ID);
    assertThat(response.caption()).isEqualTo(CAPTION);
    assertThat(response.imageS3Key()).isEqualTo(S3_KEY);
    assertThat(response.imageHash()).isEqualTo(HASH);
    assertThat(response.serverTime()).isNotNull();
    assertThat(response.imageUrl()).isNull();
    assertThat(response.failureReason()).isNull();
    assertThat(response.decisionType()).isNull();
    assertThat(response.rejectReasonCode()).isNull();
  }

  // 저장되는 MissionLog의 필드: 원본 hash/EXIF 보존, 항상 PENDING_REVIEW, image_url 비움.
  @Test
  void persistsExtractedSignalsWithPendingReviewStatus() {
    CrewParticipant participant = participant(CrewParticipantStatus.LOCKED);
    givenSubmittableContext(participant, MissionFrequencyType.DAILY);
    givenImageVerify(TAKEN_AT, HASH);
    givenSaveReturnsArgument();

    missionLogService.createMissionLog(MEMBER_UUID, request());

    MissionLog saved = captureSavedLog();
    assertThat(saved.getCrewParticipant()).isSameAs(participant);
    assertThat(saved.getImageS3Key()).isEqualTo(S3_KEY);
    assertThat(saved.getCaption()).isEqualTo(CAPTION);
    assertThat(saved.getImageHash()).isEqualTo(HASH);
    assertThat(saved.getCertificationStatus()).isEqualTo(CertificationStatus.PENDING_REVIEW);
    assertThat(saved.getImageUrl()).isNull();
    assertThat(saved.getFailureReason()).isNull();
    // exif_taken_at은 KST 기준 LocalDateTime으로 저장된다.
    assertThat(saved.getExifTakenAt()).isEqualTo(LocalDateTime.of(2026, 6, 6, 8, 0));
    // server_time은 검증에 넘긴 수신 시각과 동일한 값으로 저장된다.
    assertThat(saved.getServerTime()).isEqualTo(captureVerifyServerTime().toLocalDateTime());
    // 제출 시점 risk 판정 스냅샷도 함께 저장된다.
    assertThat(saved.getExifRisk()).isEqualTo(ExifRisk.NORMAL);
    assertThat(saved.isDuplicateHash()).isFalse();
  }

  // exifRisk/duplicate 판정 결과를 검증 응답 그대로 제출 시점 스냅샷으로 저장한다(검수 보조 신호).
  @Test
  void persistsRiskSignalsSnapshot() {
    givenSubmittableContext(participant(CrewParticipantStatus.LOCKED), MissionFrequencyType.DAILY);
    givenImageVerify(TAKEN_AT, HASH, ExifRisk.TIME_INVALID, true);
    givenSaveReturnsArgument();

    missionLogService.createMissionLog(MEMBER_UUID, request());

    MissionLog saved = captureSavedLog();
    assertThat(saved.getExifRisk()).isEqualTo(ExifRisk.TIME_INVALID);
    assertThat(saved.isDuplicateHash()).isTrue();
  }

  // EXIF가 없으면 exif_taken_at은 null로 저장하되 hash는 그대로 보존한다.
  @Test
  void storesNullExifWhenNoExifExtracted() {
    givenSubmittableContext(participant(CrewParticipantStatus.LOCKED), MissionFrequencyType.DAILY);
    givenImageVerify(null, HASH);
    givenSaveReturnsArgument();

    missionLogService.createMissionLog(MEMBER_UUID, request());

    MissionLog saved = captureSavedLog();
    assertThat(saved.getExifTakenAt()).isNull();
    assertThat(saved.getImageHash()).isEqualTo(HASH);
  }

  // evidence 순서: 원본 추출(getImageVerifyResponse) -> 기록(save) -> reEncode.
  // (활성 트랜잭션이 없는 단위 테스트에서는 reEncode가 곧바로 실행되는 fallback 경로를 탄다.)
  @Test
  void extractsAndRecordsBeforeReEncode() {
    givenSubmittableContext(participant(CrewParticipantStatus.LOCKED), MissionFrequencyType.DAILY);
    givenImageVerify(TAKEN_AT, HASH);
    givenSaveReturnsArgument();

    missionLogService.createMissionLog(MEMBER_UUID, request());

    InOrder inOrder = inOrder(missionImageService, missionLogRepository, imageProcessingPort);
    inOrder.verify(missionImageService).getImageVerifyResponse(eq(CREW_ID), eq(S3_KEY), any());
    inOrder.verify(missionLogRepository).save(any(MissionLog.class));
    inOrder.verify(imageProcessingPort).reEncode(S3_KEY);
  }

  // 트랜잭션이 활성이면 reEncode(원본 파괴)는 커밋 전에 실행되지 않고 afterCommit으로 미뤄진다.
  @Test
  void defersReEncodeUntilAfterCommit() {
    givenSubmittableContext(participant(CrewParticipantStatus.LOCKED), MissionFrequencyType.DAILY);
    givenImageVerify(TAKEN_AT, HASH);
    givenSaveReturnsArgument();

    TransactionSynchronizationManager.initSynchronization();
    try {
      missionLogService.createMissionLog(MEMBER_UUID, request());

      // 커밋 전: 로그는 저장되지만 reEncode는 아직 실행되지 않는다.
      verify(missionLogRepository).save(any(MissionLog.class));
      verify(imageProcessingPort, never()).reEncode(anyString());

      fireAfterCommit();

      // 커밋 후에만 reEncode가 실행된다.
      verify(imageProcessingPort).reEncode(S3_KEY);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  // 커밋이 롤백되면 reEncode는 실행되지 않아 원본이 보존된다.
  @Test
  void doesNotReEncodeWhenTransactionRollsBack() {
    givenSubmittableContext(participant(CrewParticipantStatus.LOCKED), MissionFrequencyType.DAILY);
    givenImageVerify(TAKEN_AT, HASH);
    givenSaveReturnsArgument();

    TransactionSynchronizationManager.initSynchronization();
    try {
      missionLogService.createMissionLog(MEMBER_UUID, request());

      fireAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

      verify(imageProcessingPort, never()).reEncode(anyString());
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  // DAILY 크루는 미션 요일 조회를 수행하지 않는다.
  @Test
  void dailyCrewSkipsMissionDayLookup() {
    givenSubmittableContext(participant(CrewParticipantStatus.LOCKED), MissionFrequencyType.DAILY);
    givenImageVerify(TAKEN_AT, HASH);
    givenSaveReturnsArgument();

    missionLogService.createMissionLog(MEMBER_UUID, request());

    verify(missionScheduleDayRepository, never())
        .existsByMissionRuleIdAndDayOfWeek(anyLong(), anyInt());
  }

  // SPECIFIC_DAYS 크루는 server_time의 ISO 요일(1~7)로 미션 가능일을 조회한다.
  @Test
  void specificDaysCrewChecksIsoDayOfWeek() {
    givenSubmittableContext(
        participant(CrewParticipantStatus.LOCKED), MissionFrequencyType.SPECIFIC_DAYS);
    givenImageVerify(TAKEN_AT, HASH);
    givenSaveReturnsArgument();

    missionLogService.createMissionLog(MEMBER_UUID, request());

    ArgumentCaptor<Integer> dayOfWeekCaptor = ArgumentCaptor.forClass(Integer.class);
    verify(missionScheduleDayRepository)
        .existsByMissionRuleIdAndDayOfWeek(eq(RULE_ID), dayOfWeekCaptor.capture());
    assertThat(dayOfWeekCaptor.getValue()).isBetween(1, 7);
  }

  // 당일 중복 검사는 [당일 00:00, 다음날 00:00) 반열린 구간을 사용한다.
  @Test
  void duplicateCheckUsesHalfOpenKstDayWindow() {
    givenSubmittableContext(participant(CrewParticipantStatus.LOCKED), MissionFrequencyType.DAILY);
    givenImageVerify(TAKEN_AT, HASH);
    givenSaveReturnsArgument();

    missionLogService.createMissionLog(MEMBER_UUID, request());

    ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
    ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(missionLogRepository)
        .existsByCrewParticipantIdAndCertificationStatusAndServerTimeGreaterThanEqualAndServerTimeLessThan(
            eq(PARTICIPANT_ID),
            eq(CertificationStatus.SUCCESS),
            startCaptor.capture(),
            endCaptor.capture());
    LocalDateTime start = startCaptor.getValue();
    LocalDateTime end = endCaptor.getValue();
    assertThat(start.toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
    assertThat(end).isEqualTo(start.plusDays(1));
  }

  private void fireAfterCommit() {
    for (TransactionSynchronization sync :
        TransactionSynchronizationManager.getSynchronizations()) {
      sync.afterCommit();
    }
  }

  private void fireAfterCompletion(int status) {
    for (TransactionSynchronization sync :
        TransactionSynchronizationManager.getSynchronizations()) {
      sync.afterCompletion(status);
    }
  }

  private MissionLogCreateRequest request() {
    return new MissionLogCreateRequest(CREW_ID, S3_KEY, CAPTION);
  }

  private CrewParticipant participant(CrewParticipantStatus status) {
    return participant(status, FAR_PAST, FAR_FUTURE);
  }

  private CrewParticipant participant(
      CrewParticipantStatus status, LocalDateTime startAt, LocalDateTime endAt) {
    CrewParticipant participant = mock(CrewParticipant.class);
    Crew crew = mock(Crew.class);
    lenient().when(participant.getId()).thenReturn(PARTICIPANT_ID);
    lenient().when(participant.getStatus()).thenReturn(status);
    lenient().when(participant.getCrew()).thenReturn(crew);
    lenient().when(crew.getId()).thenReturn(CREW_ID);
    lenient().when(crew.getStartAt()).thenReturn(startAt);
    lenient().when(crew.getEndAt()).thenReturn(endAt);
    return participant;
  }

  // 정상 제출 직전까지 통과하는 stub 묶음.
  private void givenSubmittableContext(CrewParticipant participant, MissionFrequencyType type) {
    givenParticipantFound(participant);
    givenKeyMatches(true);
    givenMissionRule(type);
    if (type == MissionFrequencyType.SPECIFIC_DAYS) {
      givenMissionDay(true);
    }
    givenSuccessLogExists(false);
    givenPendingLogExists(false);
  }

  private void givenParticipantFound(CrewParticipant participant) {
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, MEMBER_UUID))
        .willReturn(Optional.of(participant));
  }

  private void givenKeyMatches(boolean matches) {
    given(imageObjectKeyPolicy.matchesMissionKey(eq(CREW_ID), eq(PARTICIPANT_ID), eq(S3_KEY)))
        .willReturn(matches);
  }

  private void givenMissionRule(MissionFrequencyType type) {
    MissionRule rule = mock(MissionRule.class);
    given(rule.getFrequencyType()).willReturn(type);
    lenient().when(rule.getId()).thenReturn(RULE_ID);
    given(missionRuleRepository.findByCrewId(CREW_ID)).willReturn(Optional.of(rule));
  }

  private void givenMissionDay(boolean isMissionDay) {
    given(missionScheduleDayRepository.existsByMissionRuleIdAndDayOfWeek(eq(RULE_ID), anyInt()))
        .willReturn(isMissionDay);
  }

  private void givenSuccessLogExists(boolean exists) {
    given(
            missionLogRepository
                .existsByCrewParticipantIdAndCertificationStatusAndServerTimeGreaterThanEqualAndServerTimeLessThan(
                    eq(PARTICIPANT_ID), eq(CertificationStatus.SUCCESS), any(), any()))
        .willReturn(exists);
  }

  private void givenPendingLogExists(boolean exists) {
    given(
            missionLogRepository
                .existsByCrewParticipantIdAndCertificationStatusAndServerTimeGreaterThanEqualAndServerTimeLessThan(
                    eq(PARTICIPANT_ID), eq(CertificationStatus.PENDING_REVIEW), any(), any()))
        .willReturn(exists);
  }

  private void givenImageVerify(OffsetDateTime takenAt, String hash) {
    givenImageVerify(takenAt, hash, ExifRisk.NORMAL, false);
  }

  private void givenImageVerify(
      OffsetDateTime takenAt, String hash, ExifRisk exifRisk, boolean duplicate) {
    given(missionImageService.getImageVerifyResponse(eq(CREW_ID), eq(S3_KEY), any()))
        .willReturn(new ImageVerifyResponse(takenAt, hash, exifRisk, duplicate));
  }

  private void givenSaveReturnsArgument() {
    given(missionLogRepository.save(any(MissionLog.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
  }

  private MissionLog captureSavedLog() {
    ArgumentCaptor<MissionLog> captor = ArgumentCaptor.forClass(MissionLog.class);
    verify(missionLogRepository).save(captor.capture());
    return captor.getValue();
  }

  private OffsetDateTime captureVerifyServerTime() {
    ArgumentCaptor<OffsetDateTime> captor = ArgumentCaptor.forClass(OffsetDateTime.class);
    verify(missionImageService).getImageVerifyResponse(eq(CREW_ID), eq(S3_KEY), captor.capture());
    return captor.getValue();
  }
}
