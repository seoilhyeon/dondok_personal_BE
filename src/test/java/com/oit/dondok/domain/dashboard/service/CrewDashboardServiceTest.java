package com.oit.dondok.domain.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.crew.repository.CrewQueryRepository;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import com.oit.dondok.domain.dashboard.dto.response.CrewDashboardResponse;
import com.oit.dondok.domain.dashboard.dto.response.CrewDashboardResponse.ProjectionNotice;
import com.oit.dondok.domain.dashboard.dto.response.CrewDashboardResponse.ProjectionStatus;
import com.oit.dondok.domain.dashboard.repository.CrewDashboardParticipantRow;
import com.oit.dondok.domain.dashboard.repository.CrewDashboardQueryRepository;
import com.oit.dondok.domain.dashboard.repository.CrewDashboardSnapshotRow;
import com.oit.dondok.domain.dashboard.repository.CrewParticipantRosterRow;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.repository.MissionRuleRepository;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import com.oit.dondok.global.exception.CustomException;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CrewDashboardServiceTest {

  private static final Long CREW_ID = 42L;
  private static final UUID MEMBER_UUID = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
  private static final long MY_PARTICIPANT_ID = 1L;

  @Mock private CrewRepository crewRepository;
  @Mock private CrewParticipantRepository crewParticipantRepository;
  @Mock private MissionRuleRepository missionRuleRepository;
  @Mock private CrewQueryRepository crewQueryRepository;
  @Mock private SettlementRepository settlementRepository;
  @Mock private CrewDashboardQueryRepository crewDashboardQueryRepository;

  @InjectMocks private CrewDashboardService service;

  // LIVE: 최신/직전 스냅샷 기준 rank/rank_delta/예상환급금/변동/participants 산출
  @Test
  void buildsLiveDashboardWithRankAndDelta() throws Exception {
    Crew crew = crew(CrewStatus.ACTIVE);
    CrewParticipant me = participant(MY_PARTICIPANT_ID, crew, CrewParticipantStatus.LOCKED);

    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, MEMBER_UUID))
        .willReturn(Optional.of(me));
    given(crewRepository.findById(CREW_ID)).willReturn(Optional.of(crew));
    given(missionRuleRepository.findByCrewId(CREW_ID)).willReturn(Optional.of(missionRule(crew)));
    given(crewDashboardQueryRepository.findRecentProvisionalSnapshots(CREW_ID, 2))
        .willReturn(
            List.of(
                new CrewDashboardSnapshotRow(100L, LocalDate.of(2026, 6, 10), frozen(6, 10)),
                new CrewDashboardSnapshotRow(99L, LocalDate.of(2026, 6, 9), frozen(6, 9))));
    given(crewDashboardQueryRepository.findParticipantRows(List.of(100L, 99L)))
        .willReturn(
            List.of(
                row(100L, 1L, "나", 5, "0.400000", 2000L),
                row(100L, 2L, "남", 7, "0.600000", 3000L),
                row(99L, 1L, "나", 4, "0.800000", 1500L),
                row(99L, 2L, "남", 3, "0.200000", 3500L)));
    given(settlementRepository.findByCrewId(CREW_ID)).willReturn(Optional.empty());

    CrewDashboardResponse response = service.getCrewDashboard(MEMBER_UUID, CREW_ID);

    assertThat(response.projectionStatus()).isEqualTo(ProjectionStatus.LIVE);
    assertThat(response.projectionNotice()).isEqualTo(ProjectionNotice.ESTIMATED_NOT_FINAL);
    assertThat(response.settlementStatus()).isEqualTo("NONE");
    assertThat(response.settlementId()).isNull();
    assertThat(response.myDepositAmount()).isEqualTo(100_000L);
    assertThat(response.mySuccessCount()).isEqualTo(5);
    assertThat(response.myExpectedRefundAmount()).isEqualTo(2000L);
    assertThat(response.myExpectedRefundDeltaAmount()).isEqualTo(500L); // 2000 - 1500
    assertThat(response.rank()).isEqualTo(2); // 남(0.6) > 나(0.4)
    assertThat(response.rankTotal()).isEqualTo(2);
    assertThat(response.rankDelta()).isEqualTo(-1); // 직전 1위 → 현재 2위 (하락)
    assertThat(response.nextSettlementAt()).isNotNull();
    // share_ratio desc 정렬: 남(cp2) → 나(cp1)
    assertThat(response.participants()).hasSize(2);
    assertThat(response.participants().get(0).crewParticipantId()).isEqualTo(2L);
    assertThat(response.participants().get(0).isMe()).isFalse();
    assertThat(response.participants().get(1).crewParticipantId()).isEqualTo(1L);
    assertThat(response.participants().get(1).isMe()).isTrue();
    assertThat(response.participants().get(1).shareRatio()).isEqualTo("0.4");
  }

  // NOT_STARTED: 스냅샷 없어도 LOCKED 로스터로 participants 채움, share_ratio null, rank_total=참여자 수
  @Test
  void buildsNotStartedDashboardWithRosterParticipants() throws Exception {
    Crew crew = crew(CrewStatus.RECRUITING);
    CrewParticipant me = participant(MY_PARTICIPANT_ID, crew, CrewParticipantStatus.LOCKED);

    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, MEMBER_UUID))
        .willReturn(Optional.of(me));
    given(crewRepository.findById(CREW_ID)).willReturn(Optional.of(crew));
    given(missionRuleRepository.findByCrewId(CREW_ID)).willReturn(Optional.of(missionRule(crew)));
    given(crewDashboardQueryRepository.findRecentProvisionalSnapshots(CREW_ID, 2))
        .willReturn(List.of());
    given(crewDashboardQueryRepository.findLockedParticipants(CREW_ID))
        .willReturn(
            List.of(new CrewParticipantRosterRow(1L, "나"), new CrewParticipantRosterRow(2L, "남")));
    given(settlementRepository.findByCrewId(CREW_ID)).willReturn(Optional.empty());

    CrewDashboardResponse response = service.getCrewDashboard(MEMBER_UUID, CREW_ID);

    assertThat(response.projectionStatus()).isEqualTo(ProjectionStatus.NOT_STARTED);
    assertThat(response.projectionNotice()).isEqualTo(ProjectionNotice.NOT_STARTED);
    assertThat(response.mySuccessCount()).isZero();
    assertThat(response.myExpectedRefundAmount()).isNull();
    assertThat(response.rank()).isNull();
    assertThat(response.rankDelta()).isNull();
    // rank_total = 참여자 수, participants는 LOCKED 로스터로 id asc, share_ratio null
    assertThat(response.rankTotal()).isEqualTo(2);
    assertThat(response.participants()).hasSize(2);
    assertThat(response.participants().get(0).crewParticipantId()).isEqualTo(1L);
    assertThat(response.participants().get(0).isMe()).isTrue();
    assertThat(response.participants().get(0).shareRatio()).isNull();
    assertThat(response.participants().get(1).crewParticipantId()).isEqualTo(2L);
    assertThat(response.participants().get(1).isMe()).isFalse();
    assertThat(response.participants().get(1).shareRatio()).isNull();
  }

  @Test
  void throwsAccessDeniedWhenNotLockedParticipant() throws Exception {
    Crew crew = crew(CrewStatus.ACTIVE);
    CrewParticipant pending = participant(MY_PARTICIPANT_ID, crew, CrewParticipantStatus.PENDING);
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, MEMBER_UUID))
        .willReturn(Optional.of(pending));

    assertThatThrownBy(() -> service.getCrewDashboard(MEMBER_UUID, CREW_ID))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.CREW_ACCESS_DENIED);
  }

  @Test
  void throwsParticipantNotFoundWhenCrewExistsButNoParticipation() {
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, MEMBER_UUID))
        .willReturn(Optional.empty());
    given(crewRepository.existsById(CREW_ID)).willReturn(true);

    assertThatThrownBy(() -> service.getCrewDashboard(MEMBER_UUID, CREW_ID))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.PARTICIPANT_NOT_FOUND);
  }

  @Test
  void throwsCrewNotFoundWhenCrewDoesNotExist() {
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, MEMBER_UUID))
        .willReturn(Optional.empty());
    given(crewRepository.existsById(CREW_ID)).willReturn(false);

    assertThatThrownBy(() -> service.getCrewDashboard(MEMBER_UUID, CREW_ID))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.CREW_NOT_FOUND);
  }

  private static LocalDateTime frozen(int month, int day) {
    return LocalDateTime.of(2026, month, day, 9, 0);
  }

  private static CrewDashboardParticipantRow row(
      long snapshotId,
      long crewParticipantId,
      String nickname,
      int successCount,
      String shareRatio,
      long expectedRefundAmount) {
    return new CrewDashboardParticipantRow(
        snapshotId,
        crewParticipantId,
        nickname,
        successCount,
        new BigDecimal(shareRatio),
        expectedRefundAmount);
  }

  private Crew crew(CrewStatus status) throws Exception {
    Crew crew = newInstance(Crew.class);
    ReflectionTestUtils.setField(crew, "id", CREW_ID);
    ReflectionTestUtils.setField(crew, "title", "테스트 크루");
    ReflectionTestUtils.setField(crew, "status", status);
    ReflectionTestUtils.setField(crew, "startAt", LocalDateTime.of(2020, 1, 1, 0, 0));
    ReflectionTestUtils.setField(crew, "endAt", LocalDateTime.of(2099, 12, 31, 0, 0));
    return crew;
  }

  private CrewParticipant participant(long id, Crew crew, CrewParticipantStatus status)
      throws Exception {
    CrewParticipant participant = newInstance(CrewParticipant.class);
    ReflectionTestUtils.setField(participant, "id", id);
    ReflectionTestUtils.setField(participant, "crew", crew);
    ReflectionTestUtils.setField(participant, "status", status);
    ReflectionTestUtils.setField(participant, "depositAmount", 100_000L);
    return participant;
  }

  private MissionRule missionRule(Crew crew) throws Exception {
    MissionRule rule = newInstance(MissionRule.class);
    ReflectionTestUtils.setField(rule, "id", 7L);
    ReflectionTestUtils.setField(rule, "crew", crew);
    ReflectionTestUtils.setField(rule, "frequencyType", MissionFrequencyType.DAILY);
    ReflectionTestUtils.setField(rule, "dailySettlementType", DailySettlementType.A);
    return rule;
  }

  private static <T> T newInstance(Class<T> type) throws Exception {
    Constructor<T> constructor = type.getDeclaredConstructor();
    constructor.setAccessible(true);
    return constructor.newInstance();
  }
}
