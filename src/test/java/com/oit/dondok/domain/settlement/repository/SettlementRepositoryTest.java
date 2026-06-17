package com.oit.dondok.domain.settlement.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.config.JpaAuditingConfig;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementRuleContextSnapshot;
import com.oit.dondok.domain.settlement.entity.SettlementStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest(
    properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(JpaAuditingConfig.class)
class SettlementRepositoryTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 13, 0, 0);
  private static final List<SettlementStatus> RUNNABLE_STATUSES =
      List.of(SettlementStatus.PENDING, SettlementStatus.RETRY_WAIT);

  @Autowired private SettlementRepository settlementRepository;
  @Autowired private TestEntityManager entityManager;

  @Test
  void runnableSettlementQueryExcludesRetryCountGreaterThanOrEqualToMaxRetryCount() {
    Crew pendingCrew = persistCrew("pending@example.com");
    Crew retryWaitCrew = persistCrew("retry-wait@example.com");
    Crew maxRetryCrew = persistCrew("max-retry@example.com");
    Crew failedCrew = persistCrew("failed@example.com");
    persistSettlement(pendingCrew, SettlementStatus.PENDING, 0);
    persistSettlement(retryWaitCrew, SettlementStatus.RETRY_WAIT, 2);
    persistSettlement(maxRetryCrew, SettlementStatus.RETRY_WAIT, Settlement.MAX_RETRY_COUNT);
    persistSettlement(failedCrew, SettlementStatus.FAILED, 0);
    entityManager.flush();
    entityManager.clear();

    List<Long> runnableCrewIds =
        settlementRepository.findCrewIdsByStatusInAndRetryCountLessThan(
            RUNNABLE_STATUSES, Settlement.MAX_RETRY_COUNT);

    assertThat(runnableCrewIds)
        .containsExactlyInAnyOrder(pendingCrew.getId(), retryWaitCrew.getId());
  }

  @Test
  void claimRunnableReturnsZeroWhenRetryCountReachedMaxRetryCount() {
    Crew crew = persistCrew("retry-max@example.com");
    Settlement maxRetry =
        persistSettlement(crew, SettlementStatus.RETRY_WAIT, Settlement.MAX_RETRY_COUNT);
    entityManager.flush();
    entityManager.clear();

    int claimed =
        settlementRepository.claimRunnable(
            maxRetry.getId(),
            "batch-retry-test",
            NOW,
            RUNNABLE_STATUSES,
            Settlement.MAX_RETRY_COUNT);

    assertThat(claimed).isZero();
  }

  private Settlement persistSettlement(Crew crew, SettlementStatus status, int retryCount) {
    Settlement settlement =
        Settlement.createPending(
            crew,
            "batch-test",
            NOW,
            new SettlementRuleContextSnapshot(DailySettlementType.A, MissionFrequencyType.DAILY));
    ReflectionTestUtils.setField(settlement, "status", status);
    ReflectionTestUtils.setField(settlement, "retryCount", retryCount);
    return entityManager.persist(settlement);
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
