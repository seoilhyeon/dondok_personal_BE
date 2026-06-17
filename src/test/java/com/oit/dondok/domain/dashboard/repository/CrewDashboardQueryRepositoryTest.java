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
@Import({JpaAuditingConfig.class, QuerydslConfig.class, CrewDashboardQueryRepository.class})
class CrewDashboardQueryRepositoryTest {

  private static final LocalDate MISSION_DATE = LocalDate.of(2026, 6, 10);
  private static final LocalDateTime FROZEN_AT = LocalDateTime.of(2026, 6, 10, 9, 0);

  @Autowired private TestEntityManager entityManager;
  @Autowired private CrewDashboardQueryRepository repository;

  // 최신 PROVISIONAL·SUCCEEDED 스냅샷을 mission_date 내림차순으로 limit개만, FINALIZED/FAILED 제외하고 반환한다.
  @Test
  void findRecentProvisionalSnapshotsReturnsLatestProvisionalSucceededOnly() {
    Member member = persistMember("member@example.com", "회원");
    Crew crew = persistCrew(member);

    DailySettlementSnapshot day10 = persistProvisional(crew, MISSION_DATE, FROZEN_AT);
    DailySettlementSnapshot day9 =
        persistProvisional(crew, MISSION_DATE.minusDays(1), FROZEN_AT.minusDays(1));
    persistProvisional(crew, MISSION_DATE.minusDays(2), FROZEN_AT.minusDays(2));
    persistFinalized(crew, MISSION_DATE.minusDays(3), FROZEN_AT);
    persistFailedProvisional(crew, MISSION_DATE.minusDays(4), FROZEN_AT.minusDays(4));
    entityManager.flush();
    entityManager.clear();

    List<CrewDashboardSnapshotRow> rows =
        repository.findRecentProvisionalSnapshots(crew.getId(), 2);

    assertThat(rows)
        .extracting(CrewDashboardSnapshotRow::snapshotId)
        .containsExactly(day10.getId(), day9.getId());
    assertThat(rows.get(0).missionDate()).isEqualTo(MISSION_DATE);
  }

  // 스냅샷의 전 참여자 행을 닉네임과 함께 crew_participant_id 오름차순으로 반환한다.
  @Test
  void findParticipantRowsReturnsAllParticipantsWithNicknameOrderedByParticipantId() {
    Member member1 = persistMember("member1@example.com", "회원1");
    Member member2 = persistMember("member2@example.com", "회원2");
    Crew crew = persistCrew(member1);
    CrewParticipant participant1 = persistLockedParticipant(crew, member1);
    CrewParticipant participant2 = persistLockedParticipant(crew, member2);

    DailySettlementSnapshot snapshot = persistProvisional(crew, MISSION_DATE, FROZEN_AT);
    persistParticipantSnapshot(snapshot, participant1, 5, "0.400000", 2000L);
    persistParticipantSnapshot(snapshot, participant2, 3, "0.300000", 1500L);
    entityManager.flush();
    entityManager.clear();

    List<CrewDashboardParticipantRow> rows =
        repository.findParticipantRows(List.of(snapshot.getId()));

    assertThat(rows).hasSize(2);
    assertThat(rows)
        .extracting(CrewDashboardParticipantRow::crewParticipantId)
        .containsExactly(participant1.getId(), participant2.getId());
    assertThat(rows.get(0).nickname()).isEqualTo("회원1");
    assertThat(rows.get(0).successCount()).isEqualTo(5);
    assertThat(rows.get(0).shareRatio()).isEqualByComparingTo("0.400000");
    assertThat(rows.get(0).expectedRefundAmount()).isEqualTo(2000L);
    assertThat(rows.get(1).nickname()).isEqualTo("회원2");
  }

  @Test
  void findParticipantRowsReturnsEmptyWhenSnapshotIdsEmpty() {
    assertThat(repository.findParticipantRows(List.of())).isEmpty();
  }

  // LOCKED 참여자만 crew_participant_id 오름차순으로 닉네임과 함께 반환하고 비LOCKED는 제외한다.
  @Test
  void findLockedParticipantsReturnsOnlyLockedOrderedByParticipantIdWithNickname() {
    Member member1 = persistMember("locked1@example.com", "락회원1");
    Member member2 = persistMember("locked2@example.com", "락회원2");
    Member pendingMember = persistMember("pending@example.com", "대기회원");
    Crew crew = persistCrew(member1);
    CrewParticipant locked1 = persistLockedParticipant(crew, member1);
    CrewParticipant locked2 = persistLockedParticipant(crew, member2);
    persistPendingParticipant(crew, pendingMember);
    entityManager.flush();
    entityManager.clear();

    List<CrewParticipantRosterRow> rows = repository.findLockedParticipants(crew.getId());

    assertThat(rows)
        .extracting(CrewParticipantRosterRow::crewParticipantId)
        .containsExactly(locked1.getId(), locked2.getId());
    assertThat(rows.get(0).nickname()).isEqualTo("락회원1");
    assertThat(rows.get(1).nickname()).isEqualTo("락회원2");
  }

  @Test
  void findLockedParticipantsReturnsEmptyWhenNoLockedParticipants() {
    Member member = persistMember("host@example.com", "호스트");
    Crew crew = persistCrew(member);
    persistPendingParticipant(crew, member);
    entityManager.flush();
    entityManager.clear();

    assertThat(repository.findLockedParticipants(crew.getId())).isEmpty();
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

  private CrewParticipant persistPendingParticipant(Crew crew, Member member) {
    return entityManager.persistAndFlush(
        CrewParticipant.createPending(crew, member, 10_000L, LocalDateTime.of(2026, 5, 2, 9, 0)));
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
