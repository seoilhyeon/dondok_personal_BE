package com.oit.dondok.domain.point.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.config.JpaAuditingConfig;
import com.oit.dondok.config.QuerydslConfig;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.point.entity.PointAccount;
import com.oit.dondok.domain.point.entity.PointHistory;
import com.oit.dondok.domain.point.entity.PointReferenceType;
import com.oit.dondok.domain.point.entity.PointTransactionType;
import com.oit.dondok.domain.settlement.entity.ParticipantStatusSnapshot;
import com.oit.dondok.domain.settlement.entity.RemainderPolicy;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementCalculationReason;
import com.oit.dondok.domain.settlement.entity.SettlementFailureCode;
import com.oit.dondok.domain.settlement.entity.SettlementItem;
import com.oit.dondok.domain.settlement.entity.SettlementRuleContextSnapshot;
import com.oit.dondok.domain.settlement.entity.SettlementStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@ActiveProfiles("test")
@DataJpaTest
@Import({JpaAuditingConfig.class, QuerydslConfig.class, PointBalanceQueryRepository.class})
class PointBalanceQueryRepositoryTest {

  private static final Long DEPOSIT_IN_RECRUITING = 10_000L;
  private static final Long DEPOSIT_IN_ACTIVE = 20_000L;
  private static final Long DEPOSIT_IN_CLOSED = 30_000L;
  private static final LocalDateTime TEST_TIME = LocalDateTime.of(2026, 6, 1, 12, 0);

  @Autowired private TestEntityManager entityManager;
  @Autowired private PointBalanceQueryRepository pointBalanceQueryRepository;

  @Test
  void findWalletSummaryByMemberUuidReturnsAvailableAndComputedBalances() {
    Member member = persistMember("member@example.com", "회원");
    persistPointAccount(member, 50_000L, 1_000L, 3_000L);

    Crew recruitingCrew = persistCrew(member, "리쿠르팅 크루", CrewStatus.RECRUITING);
    Crew activeCrew = persistCrew(member, "액티브 크루", CrewStatus.ACTIVE);
    Crew closedCrew = persistCrew(member, "종료 크루", CrewStatus.CLOSED);
    Crew pendingCrew = persistCrew(member, "대기 크루", CrewStatus.RECRUITING);

    persistCrewParticipant(
        member, recruitingCrew, CrewParticipantStatus.LOCKED, DEPOSIT_IN_RECRUITING);
    persistCrewParticipant(member, activeCrew, CrewParticipantStatus.LOCKED, DEPOSIT_IN_ACTIVE);
    persistCrewParticipant(member, closedCrew, CrewParticipantStatus.LOCKED, DEPOSIT_IN_CLOSED);

    // 같은 멤버가 아니거나 LOCKED가 아닌 건은 집계에서 제외되어야 함
    Member otherMember = persistMember("other@example.com", "다른회원");
    persistCrewParticipant(otherMember, closedCrew, CrewParticipantStatus.LOCKED, 99_000L);
    persistCrewParticipant(member, pendingCrew, CrewParticipantStatus.PENDING, 7_000L);

    entityManager.flush();
    entityManager.clear();

    PointBalanceProjection projection =
        pointBalanceQueryRepository.findWalletSummaryByMemberUuid(member.getUuid());

    assertThat(projection).isNotNull();
    assertThat(projection.availableBalance()).isEqualTo(50_000L);
    assertThat(projection.reservedBalance()).isEqualTo(1_000L);
    assertThat(projection.lockedBalance()).isEqualTo(3_000L);
    assertThat(projection.activeLockedAmount())
        .isEqualTo(DEPOSIT_IN_RECRUITING + DEPOSIT_IN_ACTIVE);
    assertThat(projection.settlementPendingAmount()).isZero();
    assertThat(projection.updatedAt()).isNotNull();
  }

  @Test
  void findWalletSummaryByMemberUuidSumsUnpaidPendingSettlementRefundAmounts() {
    Member member = persistMember("pending-refund@example.com", "pending-refund");
    persistPointAccount(member, 50_000L, 1_000L, 3_000L);

    persistClosedSettlementItem(
        member, "pending settlement", SettlementStatus.PENDING, 10_000L, 8_000L);
    persistClosedSettlementItem(
        member, "running settlement", SettlementStatus.RUNNING, 14_000L, 6_000L);
    persistClosedSettlementItem(
        member, "retry wait settlement", SettlementStatus.RETRY_WAIT, 16_000L, 4_000L);

    entityManager.flush();
    entityManager.clear();

    PointBalanceProjection projection =
        pointBalanceQueryRepository.findWalletSummaryByMemberUuid(member.getUuid());

    assertThat(projection).isNotNull();
    assertThat(projection.settlementPendingAmount()).isEqualTo(18_000L);
    assertThat(projection.settlementFailedAmount()).isZero();
  }

  @Test
  void findWalletSummaryByMemberUuidSeparatesFailedSettlementRefundAmountsFromPending() {
    Member member = persistMember("failed-refund@example.com", "failed-refund");
    persistPointAccount(member, 50_000L, 1_000L, 3_000L);

    persistClosedSettlementItem(
        member, "pending settlement", SettlementStatus.PENDING, 10_000L, 8_000L);
    persistClosedSettlementItem(
        member, "failed settlement", SettlementStatus.FAILED, 9_000L, 7_000L);

    entityManager.flush();
    entityManager.clear();

    PointBalanceProjection projection =
        pointBalanceQueryRepository.findWalletSummaryByMemberUuid(member.getUuid());

    assertThat(projection).isNotNull();
    assertThat(projection.settlementPendingAmount()).isEqualTo(8_000L);
    assertThat(projection.settlementFailedAmount()).isEqualTo(7_000L);
  }

  @Test
  void findWalletSummaryByMemberUuidExcludesSettlementItemsAlreadyLinkedToPointHistory() {
    Member member = persistMember("paid-refund@example.com", "paid-refund");
    persistPointAccount(member, 50_000L, 1_000L, 3_000L);

    persistClosedSettlementItem(
        member, "unpaid pending settlement", SettlementStatus.PENDING, 10_000L, 8_000L);
    SettlementItem paidPendingItem =
        persistClosedSettlementItem(
            member, "paid pending settlement", SettlementStatus.PENDING, 40_000L, 11_000L);
    linkRefundHistory(paidPendingItem);

    persistClosedSettlementItem(
        member, "unpaid failed settlement", SettlementStatus.FAILED, 9_000L, 7_000L);
    SettlementItem paidFailedItem =
        persistClosedSettlementItem(
            member, "paid failed settlement", SettlementStatus.FAILED, 15_000L, 13_000L);
    linkRefundHistory(paidFailedItem);

    entityManager.flush();
    entityManager.clear();

    PointBalanceProjection projection =
        pointBalanceQueryRepository.findWalletSummaryByMemberUuid(member.getUuid());

    assertThat(projection).isNotNull();
    assertThat(projection.settlementPendingAmount()).isEqualTo(8_000L);
    assertThat(projection.settlementFailedAmount()).isEqualTo(7_000L);
  }

  @Test
  void findWalletSummaryByMemberUuidUsesRefundAmountInsteadOfDepositAmount() {
    Member member = persistMember("refund-only@example.com", "refund-only");
    persistPointAccount(member, 50_000L, 1_000L, 3_000L);

    persistClosedSettlementItem(
        member, "pending settlement", SettlementStatus.PENDING, 10_000L, 8_000L);
    persistClosedSettlementItem(
        member, "failed settlement", SettlementStatus.FAILED, 9_000L, 7_000L);

    entityManager.flush();
    entityManager.clear();

    PointBalanceProjection projection =
        pointBalanceQueryRepository.findWalletSummaryByMemberUuid(member.getUuid());

    assertThat(projection).isNotNull();
    assertThat(projection.settlementPendingAmount()).isEqualTo(8_000L);
    assertThat(projection.settlementFailedAmount()).isEqualTo(7_000L);
  }

  @Test
  void findWalletSummaryByMemberUuidComputesActiveLockedAndSettlementAmountsIndependently() {
    Member member = persistMember("independent-aggregate@example.com", "independent-aggregate");
    persistPointAccount(member, 50_000L, 1_000L, 3_000L);

    Crew recruitingCrew = persistCrew(member, "recruiting crew", CrewStatus.RECRUITING);
    Crew activeCrew = persistCrew(member, "active crew", CrewStatus.ACTIVE);
    Crew closedWithoutSettlementCrew =
        persistCrew(member, "closed without settlement", CrewStatus.CLOSED);
    persistCrewParticipant(member, recruitingCrew, CrewParticipantStatus.LOCKED, 10_000L);
    persistCrewParticipant(member, activeCrew, CrewParticipantStatus.LOCKED, 20_000L);
    persistCrewParticipant(
        member, closedWithoutSettlementCrew, CrewParticipantStatus.LOCKED, 30_000L);

    persistClosedSettlementItem(
        member, "pending settlement", SettlementStatus.PENDING, 10_000L, 8_000L);
    persistClosedSettlementItem(
        member, "running settlement", SettlementStatus.RUNNING, 14_000L, 6_000L);

    entityManager.flush();
    entityManager.clear();

    PointBalanceProjection projection =
        pointBalanceQueryRepository.findWalletSummaryByMemberUuid(member.getUuid());

    assertThat(projection).isNotNull();
    assertThat(projection.lockedBalance()).isEqualTo(3_000L);
    assertThat(projection.activeLockedAmount()).isEqualTo(30_000L);
    assertThat(projection.settlementPendingAmount()).isEqualTo(14_000L);
    assertThat(projection.activeLockedAmount() + projection.settlementPendingAmount())
        .isNotEqualTo(projection.lockedBalance());
  }

  @Test
  void findWalletSummaryByMemberUuidReturnsNullWhenAccountNotFound() {
    UUID randomUuid = UUID.randomUUID();

    PointBalanceProjection projection =
        pointBalanceQueryRepository.findWalletSummaryByMemberUuid(randomUuid);

    assertThat(projection).isNull();
  }

  private Member persistMember(String email, String nickname) {
    return entityManager.persistAndFlush(Member.create(email, "password-hash", nickname));
  }

  private void persistPointAccount(Member member, long available, long reserved, long locked) {
    PointAccount account = PointAccount.create(member);
    ReflectionTestUtils.setField(account, "availableBalance", available);
    ReflectionTestUtils.setField(account, "reservedBalance", reserved);
    ReflectionTestUtils.setField(account, "lockedBalance", locked);
    entityManager.persistAndFlush(account);
  }

  private Crew persistCrew(Member host, String title, CrewStatus status) {
    Crew crew =
        Crew.create(
            host,
            title,
            title + " 설명",
            null,
            "OTHER",
            "{\"version\":1}",
            HostPolicyVersion.HOST_POLICY_V1,
            LocalDateTime.of(2026, 6, 1, 12, 0),
            5_000L,
            2,
            10,
            LocalDateTime.of(2026, 6, 8, 20, 0),
            LocalDateTime.of(2026, 6, 12, 0, 0),
            LocalDateTime.of(2026, 7, 12, 23, 59));
    ReflectionTestUtils.setField(crew, "status", status);
    return entityManager.persistAndFlush(crew);
  }

  private CrewParticipant persistCrewParticipant(
      Member member, Crew crew, CrewParticipantStatus status, Long depositAmount) {
    CrewParticipant participant;
    if (status == CrewParticipantStatus.PENDING) {
      participant = CrewParticipant.createPending(crew, member, depositAmount, LocalDateTime.now());
    } else {
      participant = CrewParticipant.create(crew, member, depositAmount, LocalDateTime.now());
      ReflectionTestUtils.setField(participant, "status", status);
    }
    return entityManager.persistAndFlush(participant);
  }

  private Settlement persistSettlement(Crew crew, SettlementStatus status) {
    Settlement settlement = BeanUtils.instantiateClass(Settlement.class);
    ReflectionTestUtils.setField(settlement, "crew", crew);
    ReflectionTestUtils.setField(settlement, "status", status);
    ReflectionTestUtils.setField(settlement, "baselineFrozenAt", TEST_TIME);
    ReflectionTestUtils.setField(settlement, "batchRunKey", "batch-" + crew.getId());
    ReflectionTestUtils.setField(settlement, "retryCount", 0);
    ReflectionTestUtils.setField(settlement, "totalParticipants", 1);
    ReflectionTestUtils.setField(settlement, "totalLockedAmount", crew.getDepositAmount());
    ReflectionTestUtils.setField(settlement, "totalRecognizedSuccess", 1);
    ReflectionTestUtils.setField(settlement, "totalBaseRefundAmount", crew.getDepositAmount());
    ReflectionTestUtils.setField(settlement, "totalRemainderAmount", 0L);
    ReflectionTestUtils.setField(settlement, "remainderPolicy", RemainderPolicy.HOST_REMAINDER);
    ReflectionTestUtils.setField(settlement, "failureCode", failureCode(status));
    ReflectionTestUtils.setField(settlement, "failureMessage", null);
    ReflectionTestUtils.setField(settlement, "algorithmVersion", "test-v1");
    ReflectionTestUtils.setField(
        settlement,
        "ruleContextSnapshot",
        SettlementRuleContextSnapshot.parse(
            "{\"daily_settlement_type\":\"WEEKLY\",\"frequency_type\":\"WEEK\"}"));
    ReflectionTestUtils.setField(settlement, "startedAt", TEST_TIME);
    ReflectionTestUtils.setField(settlement, "finishedAt", TEST_TIME.plusMinutes(1));
    ReflectionTestUtils.setField(settlement, "version", 0L);
    return entityManager.persistAndFlush(settlement);
  }

  private SettlementFailureCode failureCode(SettlementStatus status) {
    return status == SettlementStatus.FAILED ? SettlementFailureCode.POINT_CREDIT_FAILED : null;
  }

  private SettlementItem persistClosedSettlementItem(
      Member member,
      String crewTitle,
      SettlementStatus status,
      long depositAmount,
      long refundAmount) {
    Crew crew = persistCrew(member, crewTitle, CrewStatus.CLOSED);
    CrewParticipant participant =
        persistCrewParticipant(member, crew, CrewParticipantStatus.LOCKED, depositAmount);
    return persistSettlementItem(
        persistSettlement(crew, status), participant, depositAmount, refundAmount);
  }

  private SettlementItem persistSettlementItem(
      Settlement settlement, CrewParticipant participant, long depositAmount, long refundAmount) {
    SettlementItem item = BeanUtils.instantiateClass(SettlementItem.class);
    ReflectionTestUtils.setField(item, "settlement", settlement);
    ReflectionTestUtils.setField(item, "crewParticipant", participant);
    ReflectionTestUtils.setField(item, "member", participant.getMember());
    ReflectionTestUtils.setField(
        item, "participantStatusSnapshot", ParticipantStatusSnapshot.LOCKED);
    ReflectionTestUtils.setField(item, "depositAmount", depositAmount);
    ReflectionTestUtils.setField(item, "successCountRaw", 1);
    ReflectionTestUtils.setField(item, "recognizedSuccessCount", 1);
    ReflectionTestUtils.setField(item, "recognizedDatesCount", 1);
    ReflectionTestUtils.setField(item, "excludedSuccessCount", 0);
    ReflectionTestUtils.setField(item, "periodStartAt", TEST_TIME);
    ReflectionTestUtils.setField(item, "periodEndAt", TEST_TIME.plusDays(7));
    ReflectionTestUtils.setField(item, "shareRatio", BigDecimal.ONE.setScale(6));
    ReflectionTestUtils.setField(item, "baseRefundAmount", refundAmount);
    ReflectionTestUtils.setField(item, "remainderBonusAmount", 0L);
    ReflectionTestUtils.setField(item, "refundAmount", refundAmount);
    ReflectionTestUtils.setField(item, "effectiveModerationSnapshot", null);
    ReflectionTestUtils.setField(item, "moderationChainRef", null);
    ReflectionTestUtils.setField(
        item, "calculationReason", SettlementCalculationReason.parse("{\"reason\":\"test\"}"));
    return entityManager.persistAndFlush(item);
  }

  private void linkRefundHistory(SettlementItem item) {
    PointHistory history =
        PointHistory.create(
            item.getMember(),
            item.getRefundAmount(),
            50_000L,
            1_000L,
            3_000L,
            PointTransactionType.CREW_SETTLEMENT_REFUND,
            PointReferenceType.SETTLEMENT_ITEM,
            item.getId(),
            "crew:%d:participant:%d:settlement-refund:final"
                .formatted(
                    item.getCrewParticipant().getCrew().getId(),
                    item.getCrewParticipant().getId()));
    entityManager.persistAndFlush(history);
    item.linkPointHistory(history);
    entityManager.persistAndFlush(item);
  }
}
