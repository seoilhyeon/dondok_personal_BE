package com.oit.dondok.domain.settlement.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.config.JpaAuditingConfig;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.settlement.entity.DailySettlementPhase;
import com.oit.dondok.domain.settlement.entity.DailySettlementSnapshot;
import com.oit.dondok.domain.settlement.entity.DailySettlementStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest(
    properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(JpaAuditingConfig.class)
class DailySettlementSnapshotRepositoryTest {

  private static final LocalDate MISSION_DATE = LocalDate.of(2026, 6, 15);
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 17, 12, 15);
  private static final LocalDateTime STALE_BEFORE = NOW.minusHours(1);

  @Autowired private DailySettlementSnapshotRepository dailySettlementSnapshotRepository;
  @Autowired private TestEntityManager entityManager;

  @Test
  void claimRetryTargetChangesFailedSnapshotToRetryingOnce() {
    DailySettlementSnapshot snapshot = persistFailedSnapshot("claim-once@example.com", 1);
    entityManager.flush();
    entityManager.clear();

    int firstClaim = claim(snapshot.getId());
    int secondClaim = claim(snapshot.getId());
    entityManager.flush();
    entityManager.clear();

    DailySettlementSnapshot claimed =
        entityManager.find(DailySettlementSnapshot.class, snapshot.getId());
    assertThat(firstClaim).isEqualTo(1);
    assertThat(secondClaim).isZero();
    assertThat(claimed.getStatus()).isEqualTo(DailySettlementStatus.RETRYING);
    assertThat(claimed.getBatchRunKey()).isEqualTo("retry-batch-key");
    assertThat(claimed.getFrozenAt()).isEqualTo(NOW);
  }

  @Test
  void claimRetryTargetSkipsFailedSnapshotFailedAtSameBatchNow() {
    DailySettlementSnapshot snapshot = persistFailedSnapshot("same-sweep@example.com", 1);
    ReflectionTestUtils.setField(snapshot, "frozenAt", NOW);
    entityManager.flush();
    entityManager.clear();

    List<Long> targetIds = findRetryTargetIds();
    int claimed = claim(snapshot.getId());

    assertThat(targetIds).isEmpty();
    assertThat(claimed).isZero();
  }

  @Test
  void claimRetryTargetSkipsFailedSnapshotWhenRetryCountReachedMax() {
    DailySettlementSnapshot snapshot =
        persistFailedSnapshot("retry-max@example.com", DailySettlementSnapshot.MAX_RETRY_COUNT);
    entityManager.flush();
    entityManager.clear();

    int claimed = claim(snapshot.getId());

    assertThat(claimed).isZero();
  }

  @Test
  void claimRetryTargetReclaimsStaleRetryingSnapshot() {
    DailySettlementSnapshot snapshot = persistFailedSnapshot("stale-retrying@example.com", 1);
    ReflectionTestUtils.setField(snapshot, "status", DailySettlementStatus.RETRYING);
    ReflectionTestUtils.setField(snapshot, "frozenAt", STALE_BEFORE.minusMinutes(1));
    entityManager.flush();
    entityManager.clear();

    List<Long> targetIds = findRetryTargetIds();
    int claimed = claim(snapshot.getId());

    assertThat(targetIds).containsExactly(snapshot.getId());
    assertThat(claimed).isEqualTo(1);
  }

  @Test
  void claimRetryTargetSkipsFreshRetryingSnapshot() {
    DailySettlementSnapshot snapshot = persistFailedSnapshot("fresh-retrying@example.com", 1);
    ReflectionTestUtils.setField(snapshot, "status", DailySettlementStatus.RETRYING);
    ReflectionTestUtils.setField(snapshot, "frozenAt", STALE_BEFORE);
    entityManager.flush();
    entityManager.clear();

    List<Long> targetIds = findRetryTargetIds();
    int claimed = claim(snapshot.getId());

    assertThat(targetIds).isEmpty();
    assertThat(claimed).isZero();
  }

  @Test
  void claimRecoveryTargetChangesRetryExhaustedFinalizedSnapshotToRetrying() {
    DailySettlementSnapshot snapshot =
        persistFailedSnapshot(
            "recovery-finalized@example.com", DailySettlementSnapshot.MAX_RETRY_COUNT);
    entityManager.flush();
    entityManager.clear();

    int claimed = claimRecovery(snapshot.getId());
    entityManager.flush();
    entityManager.clear();

    DailySettlementSnapshot claimedSnapshot =
        entityManager.find(DailySettlementSnapshot.class, snapshot.getId());
    assertThat(claimed).isEqualTo(1);
    assertThat(claimedSnapshot.getStatus()).isEqualTo(DailySettlementStatus.RETRYING);
    assertThat(claimedSnapshot.getBatchRunKey()).isEqualTo("recovery-batch-key");
    assertThat(claimedSnapshot.getFrozenAt()).isEqualTo(NOW);
  }

  @Test
  void claimRecoveryTargetSkipsFailedSnapshotBeforeRetryCountReachedMax() {
    DailySettlementSnapshot snapshot =
        persistFailedSnapshot(
            "recovery-before-max@example.com", DailySettlementSnapshot.MAX_RETRY_COUNT - 1);
    entityManager.flush();
    entityManager.clear();

    int claimed = claimRecovery(snapshot.getId());

    assertThat(claimed).isZero();
  }

  @Test
  void claimRecoveryTargetSkipsProvisionalSnapshot() {
    Crew crew = persistCrew("recovery-provisional@example.com");
    DailySettlementSnapshot snapshot =
        DailySettlementSnapshot.provisionalFailed(
            crew,
            MISSION_DATE,
            DailySettlementType.A,
            MissionFrequencyType.DAILY,
            "failed-batch-key",
            NOW.minusDays(1),
            "실패");
    ReflectionTestUtils.setField(snapshot, "retryCount", DailySettlementSnapshot.MAX_RETRY_COUNT);
    entityManager.persist(snapshot);
    entityManager.flush();
    entityManager.clear();

    int claimed = claimRecovery(snapshot.getId());

    assertThat(claimed).isZero();
  }

  @Test
  void findRetryOwnerForUpdateReturnsOnlyMatchingRetryOwner() {
    DailySettlementSnapshot snapshot = persistFailedSnapshot("owner-lock@example.com", 1);
    ReflectionTestUtils.setField(snapshot, "status", DailySettlementStatus.RETRYING);
    ReflectionTestUtils.setField(snapshot, "batchRunKey", "owner-key");
    entityManager.flush();
    entityManager.clear();

    assertThat(
            dailySettlementSnapshotRepository.findRetryOwnerForUpdate(
                snapshot.getId(), DailySettlementStatus.RETRYING, "owner-key"))
        .isPresent();
    assertThat(
            dailySettlementSnapshotRepository.findRetryOwnerForUpdate(
                snapshot.getId(), DailySettlementStatus.RETRYING, "other-owner-key"))
        .isEmpty();
  }

  private List<Long> findRetryTargetIds() {
    return dailySettlementSnapshotRepository.findRetryTargetIds(
        DailySettlementStatus.FAILED,
        DailySettlementStatus.RETRYING,
        DailySettlementSnapshot.MAX_RETRY_COUNT,
        NOW,
        STALE_BEFORE,
        PageRequest.of(0, 50));
  }

  private int claim(Long snapshotId) {
    return dailySettlementSnapshotRepository.claimRetryTarget(
        snapshotId,
        DailySettlementStatus.FAILED,
        DailySettlementStatus.RETRYING,
        DailySettlementSnapshot.MAX_RETRY_COUNT,
        NOW,
        STALE_BEFORE,
        "retry-batch-key",
        NOW);
  }

  private int claimRecovery(Long snapshotId) {
    return dailySettlementSnapshotRepository.claimRecoveryTarget(
        snapshotId,
        DailySettlementPhase.FINALIZED,
        DailySettlementStatus.FAILED,
        DailySettlementStatus.RETRYING,
        DailySettlementSnapshot.MAX_RETRY_COUNT,
        "recovery-batch-key",
        NOW);
  }

  private DailySettlementSnapshot persistFailedSnapshot(String hostEmail, int retryCount) {
    Crew crew = persistCrew(hostEmail);
    DailySettlementSnapshot snapshot =
        DailySettlementSnapshot.finalizedFailed(
            crew,
            MISSION_DATE,
            DailySettlementType.A,
            MissionFrequencyType.DAILY,
            "failed-batch-key",
            NOW.minusDays(1),
            "실패");
    ReflectionTestUtils.setField(snapshot, "retryCount", retryCount);
    return entityManager.persist(snapshot);
  }

  private Crew persistCrew(String hostEmail) {
    Member host = entityManager.persist(Member.create(hostEmail, "password", hostEmail));
    return entityManager.persist(
        Crew.create(
            host,
            "crew",
            "description",
            null,
            "category",
            "{}",
            HostPolicyVersion.HOST_POLICY_V1,
            NOW.minusDays(20),
            1_000L,
            2,
            10,
            NOW.minusDays(20),
            NOW.minusDays(10),
            NOW.minusDays(1)));
  }
}
