package com.oit.dondok.domain.settlement.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.member.entity.Member;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SettlementTest {

  @Test
  void createPendingSetsDefaultsAndRequiredFields() {
    Crew crew = buildCrew();
    LocalDateTime now = LocalDateTime.now();
    String batchRunKey = "batch-key";
    SettlementRuleContextSnapshot snapshot = settlementRuleContextSnapshot();

    Settlement settlement = Settlement.createPending(crew, batchRunKey, now, snapshot);

    assertThat(settlement.getCrew()).isEqualTo(crew);
    assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PENDING);
    assertThat(settlement.getRetryCount()).isEqualTo(0);
    assertThat(settlement.getTotalParticipants()).isZero();
    assertThat(settlement.getTotalLockedAmount()).isZero();
    assertThat(settlement.getTotalRecognizedSuccess()).isZero();
    assertThat(settlement.getTotalBaseRefundAmount()).isZero();
    assertThat(settlement.getTotalRemainderAmount()).isZero();
    assertThat(settlement.getRemainderPolicy()).isEqualTo(RemainderPolicy.HOST_REMAINDER);
    assertThat(settlement.getBaselineFrozenAt()).isEqualTo(now);
    assertThat(settlement.getBatchRunKey()).isEqualTo(batchRunKey);
    assertThat(settlement.getAlgorithmVersion()).isEqualTo("settlement-v1");
    assertThat(settlement.getRuleContextSnapshot()).isEqualTo(snapshot);
  }

  @Test
  void createPendingRejectsMissingRequiredArguments() {
    Crew crew = buildCrew();

    assertThatThrownBy(
            () ->
                Settlement.createPending(
                    null, "batch", LocalDateTime.now(), settlementRuleContextSnapshot()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> Settlement.createPending(crew, "batch", null, settlementRuleContextSnapshot()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> Settlement.createPending(crew, "batch", LocalDateTime.now(), null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void updateTotalsOverwritesValues() {
    Settlement settlement =
        Settlement.createPending(
            buildCrew(), "batch", LocalDateTime.now(), settlementRuleContextSnapshot());

    settlement.updateTotals(3, 12_000L, 5, 4_500L, 100L, RemainderPolicy.HOST_REMAINDER);

    assertThat(settlement.getTotalParticipants()).isEqualTo(3);
    assertThat(settlement.getTotalLockedAmount()).isEqualTo(12_000L);
    assertThat(settlement.getTotalRecognizedSuccess()).isEqualTo(5);
    assertThat(settlement.getTotalBaseRefundAmount()).isEqualTo(4_500L);
    assertThat(settlement.getTotalRemainderAmount()).isEqualTo(100L);
    assertThat(settlement.getRemainderPolicy()).isEqualTo(RemainderPolicy.HOST_REMAINDER);
  }

  @Test
  void markSucceededRequiresRunningStatusAndClearsFailure() {
    Settlement settlement =
        Settlement.createPending(
            buildCrew(), "batch", LocalDateTime.now(), settlementRuleContextSnapshot());
    ReflectionTestUtils.setField(settlement, "status", SettlementStatus.RUNNING);
    ReflectionTestUtils.setField(
        settlement, "failureCode", SettlementFailureCode.CALCULATION_FAILED);
    ReflectionTestUtils.setField(settlement, "failureMessage", "error");

    LocalDateTime finishedAt = LocalDateTime.now();
    settlement.markSucceeded(finishedAt);

    assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.SUCCEEDED);
    assertThat(settlement.getFinishedAt()).isEqualTo(finishedAt);
    assertThat(settlement.getFailureCode()).isNull();
    assertThat(settlement.getFailureMessage()).isNull();
  }

  @Test
  void markSucceededRejectsIfNotRunning() {
    Settlement settlement =
        Settlement.createPending(
            buildCrew(), "batch", LocalDateTime.now(), settlementRuleContextSnapshot());

    assertThatThrownBy(() -> settlement.markSucceeded(LocalDateTime.now()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void markFailedAttemptIncrementsRetryAndTransitionsState() {
    Settlement settlement =
        Settlement.createPending(
            buildCrew(), "batch", LocalDateTime.now(), settlementRuleContextSnapshot());
    ReflectionTestUtils.setField(settlement, "status", SettlementStatus.RUNNING);

    settlement.markFailedAttempt(
        SettlementFailureCode.POINT_CREDIT_FAILED, "temporary fail", LocalDateTime.now());

    assertThat(settlement.getRetryCount()).isEqualTo(1);
    assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.RETRY_WAIT);
    assertThat(settlement.getFailureCode()).isEqualTo(SettlementFailureCode.POINT_CREDIT_FAILED);
    assertThat(settlement.getFailureMessage()).isEqualTo("temporary fail");

    ReflectionTestUtils.setField(settlement, "status", SettlementStatus.RUNNING);
    ReflectionTestUtils.setField(settlement, "retryCount", 2);

    settlement.markFailedAttempt(SettlementFailureCode.UNKNOWN, "final fail", LocalDateTime.now());

    assertThat(settlement.getRetryCount()).isEqualTo(3);
    assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.FAILED);
  }

  @Test
  void markFailedAttemptRejectsIfNotRunning() {
    Settlement settlement =
        Settlement.createPending(
            buildCrew(), "batch", LocalDateTime.now(), settlementRuleContextSnapshot());

    assertThatThrownBy(
            () ->
                settlement.markFailedAttempt(
                    SettlementFailureCode.UNKNOWN, "x", LocalDateTime.now()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void markFailedAttemptTruncatesLongFailureMessageTo500Chars() {
    Settlement settlement =
        Settlement.createPending(
            buildCrew(), "batch", LocalDateTime.now(), settlementRuleContextSnapshot());
    ReflectionTestUtils.setField(settlement, "status", SettlementStatus.RUNNING);
    String longMessage = "A".repeat(600);

    settlement.markFailedAttempt(SettlementFailureCode.UNKNOWN, longMessage, LocalDateTime.now());

    assertThat(settlement.getFailureMessage()).hasSize(500);
  }

  private SettlementRuleContextSnapshot settlementRuleContextSnapshot() {
    return new SettlementRuleContextSnapshot("BASIC", "WEEKLY");
  }

  private Crew buildCrew() {
    return Crew.create(
        buildMember(),
        "제목",
        "설명",
        null,
        "EXERCISE",
        "{\"host\":true}",
        HostPolicyVersion.HOST_POLICY_V1,
        LocalDateTime.now(),
        10_000L,
        2,
        3,
        LocalDateTime.now().plusDays(1),
        LocalDateTime.now().plusDays(2),
        LocalDateTime.now().plusDays(30));
  }

  private Member buildMember() {
    return Member.create("member@example.com", "pw", "닉네임");
  }
}
