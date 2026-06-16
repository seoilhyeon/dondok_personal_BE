package com.oit.dondok.domain.dashboard.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.config.JpaAuditingConfig;
import com.oit.dondok.config.QuerydslConfig;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.settlement.entity.DailySettlementParticipantSnapshot;
import com.oit.dondok.domain.settlement.entity.DailySettlementSnapshot;
import com.oit.dondok.domain.settlement.entity.DailySettlementStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@ActiveProfiles("test")
@DataJpaTest
@Import({JpaAuditingConfig.class, QuerydslConfig.class, DashboardProjectionQueryRepository.class})
class DashboardProjectionQueryRepositoryTest {

  private static final LocalDate MISSION_DATE = LocalDate.of(2026, 6, 10);
  private static final LocalDateTime FROZEN_AT = LocalDateTime.of(2026, 6, 10, 9, 0);

  @Autowired private TestEntityManager entityManager;
  @Autowired private DashboardProjectionQueryRepository repository;

  // PROVISIONAL·SUCCEEDED 스냅샷의 회원 값을 mission_date 내림차순으로 반환한다.
  @Test
  void returnsMemberProjectionRowsOrderedByMissionDateDesc() {
    Member member = persistMember("member@example.com", "회원");
    Crew crew = persistCrew(member);
    CrewParticipant participant = persistLockedParticipant(crew, member);

    DailySettlementSnapshot latest = persistProvisional(crew, MISSION_DATE, FROZEN_AT);
    persistParticipantSnapshot(latest, participant, 5, "0.400000", 2000L);
    DailySettlementSnapshot previous =
        persistProvisional(crew, MISSION_DATE.minusDays(1), FROZEN_AT.minusDays(1));
    persistParticipantSnapshot(previous, participant, 3, "0.300000", 1500L);
    entityManager.flush();
    entityManager.clear();

    List<DashboardProjectionRow> rows =
        repository.findProvisionalProjectionRows(member.getId(), List.of(crew.getId()));

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).missionDate()).isEqualTo(MISSION_DATE);
    assertThat(rows.get(0).shareRatio()).isEqualByComparingTo("0.400000");
    assertThat(rows.get(0).expectedRefundAmount()).isEqualTo(2000L);
    assertThat(rows.get(1).missionDate()).isEqualTo(MISSION_DATE.minusDays(1));
    assertThat(rows.get(1).expectedRefundAmount()).isEqualTo(1500L);
  }

  // FINALIZED phase / FAILED status 스냅샷은 제외한다.
  @Test
  void excludesFinalizedAndFailedSnapshots() {
    Member member = persistMember("member@example.com", "회원");
    Crew crew = persistCrew(member);
    CrewParticipant participant = persistLockedParticipant(crew, member);

    DailySettlementSnapshot provisional = persistProvisional(crew, MISSION_DATE, FROZEN_AT);
    persistParticipantSnapshot(provisional, participant, 5, "0.400000", 2000L);
    DailySettlementSnapshot finalized =
        persistFinalized(crew, MISSION_DATE.minusDays(3), FROZEN_AT);
    persistParticipantSnapshot(finalized, participant, 4, "0.350000", 1800L);
    DailySettlementSnapshot failed =
        persistFailedProvisional(crew, MISSION_DATE.minusDays(1), FROZEN_AT.minusDays(1));
    persistParticipantSnapshot(failed, participant, 2, "0.200000", 1000L);
    entityManager.flush();
    entityManager.clear();

    List<DashboardProjectionRow> rows =
        repository.findProvisionalProjectionRows(member.getId(), List.of(crew.getId()));

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).missionDate()).isEqualTo(MISSION_DATE);
    assertThat(rows.get(0).expectedRefundAmount()).isEqualTo(2000L);
  }

  // 회원이 해당 스냅샷에 참여 행이 없으면 projection 값은 null이지만 행(배치)은 반환한다.
  @Test
  void returnsNullMemberValuesWhenMemberAbsentInSnapshot() {
    Member member = persistMember("member@example.com", "회원");
    Member other = persistMember("other@example.com", "다른회원");
    Crew crew = persistCrew(member);
    CrewParticipant otherParticipant = persistLockedParticipant(crew, other);

    DailySettlementSnapshot snapshot = persistProvisional(crew, MISSION_DATE, FROZEN_AT);
    persistParticipantSnapshot(snapshot, otherParticipant, 5, "0.400000", 2000L);
    entityManager.flush();
    entityManager.clear();

    List<DashboardProjectionRow> rows =
        repository.findProvisionalProjectionRows(member.getId(), List.of(crew.getId()));

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).shareRatio()).isNull();
    assertThat(rows.get(0).expectedRefundAmount()).isNull();
  }

  @Test
  void returnsEmptyWhenCrewIdsEmpty() {
    Member member = persistMember("member@example.com", "회원");

    List<DashboardProjectionRow> rows =
        repository.findProvisionalProjectionRows(member.getId(), List.of());

    assertThat(rows).isEmpty();
  }

  private Member persistMember(String email, String nickname) {
    return entityManager.persistAndFlush(Member.create(email, "password-hash", nickname));
  }

  private Crew persistCrew(Member host) {
    LocalDateTime now = LocalDateTime.of(2026, 5, 1, 9, 0);
    return entityManager.persistAndFlush(
        Crew.create(
            host,
            "크루",
            "크루 설명",
            null,
            "OTHER",
            "{}",
            HostPolicyVersion.HOST_POLICY_V1,
            now,
            10_000L,
            2,
            5,
            now.plusDays(3),
            now.plusDays(4),
            now.plusDays(30)));
  }

  private CrewParticipant persistLockedParticipant(Crew crew, Member member) {
    return entityManager.persistAndFlush(
        CrewParticipant.create(crew, member, 10_000L, LocalDateTime.of(2026, 5, 2, 9, 0)));
  }

  private DailySettlementSnapshot persistProvisional(
      Crew crew, LocalDate missionDate, LocalDateTime frozenAt) {
    return entityManager.persistAndFlush(
        DailySettlementSnapshot.provisional(
            crew,
            missionDate,
            DailySettlementType.A,
            MissionFrequencyType.DAILY,
            "run-" + missionDate,
            frozenAt,
            1,
            5,
            10_000L));
  }

  private DailySettlementSnapshot persistFinalized(
      Crew crew, LocalDate missionDate, LocalDateTime frozenAt) {
    return entityManager.persistAndFlush(
        DailySettlementSnapshot.finalized(
            crew,
            missionDate,
            DailySettlementType.A,
            MissionFrequencyType.DAILY,
            "run-fin-" + missionDate,
            frozenAt,
            1,
            5,
            10_000L));
  }

  private DailySettlementSnapshot persistFailedProvisional(
      Crew crew, LocalDate missionDate, LocalDateTime frozenAt) {
    DailySettlementSnapshot snapshot =
        DailySettlementSnapshot.provisional(
            crew,
            missionDate,
            DailySettlementType.A,
            MissionFrequencyType.DAILY,
            "run-fail-" + missionDate,
            frozenAt,
            1,
            5,
            10_000L);
    ReflectionTestUtils.setField(snapshot, "status", DailySettlementStatus.FAILED);
    return entityManager.persistAndFlush(snapshot);
  }

  private void persistParticipantSnapshot(
      DailySettlementSnapshot snapshot,
      CrewParticipant participant,
      int successCount,
      String shareRatio,
      long expectedRefundAmount) {
    entityManager.persistAndFlush(
        DailySettlementParticipantSnapshot.create(
            snapshot, participant, successCount, new BigDecimal(shareRatio), expectedRefundAmount));
  }
}
