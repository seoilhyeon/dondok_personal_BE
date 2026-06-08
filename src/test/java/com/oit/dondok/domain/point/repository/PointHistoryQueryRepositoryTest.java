package com.oit.dondok.domain.point.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.config.JpaAuditingConfig;
import com.oit.dondok.config.QuerydslConfig;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.point.entity.PointHistory;
import com.oit.dondok.domain.point.entity.PointReferenceType;
import com.oit.dondok.domain.point.entity.PointTransactionType;
import com.oit.dondok.domain.settlement.entity.ParticipantStatusSnapshot;
import com.oit.dondok.domain.settlement.entity.RemainderPolicy;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementItem;
import com.oit.dondok.domain.settlement.entity.SettlementStatus;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@ActiveProfiles("test")
@DataJpaTest
@Import({JpaAuditingConfig.class, QuerydslConfig.class, PointHistoryQueryRepository.class})
class PointHistoryQueryRepositoryTest {

  private int idempotencySequence = 0;

  @Autowired private TestEntityManager entityManager;
  @Autowired private PointHistoryQueryRepository pointHistoryQueryRepository;

  @Test
  void findHistoriesByCursorReturnsNewestFirstAndSupportsCursorPagination() {
    Member member = persistMember("member@example.com", "회원");

    PointHistory oldHistory =
        persistPointHistory(
            member,
            1_000L,
            PointTransactionType.POINT_CHARGE,
            PointReferenceType.POINT_CHARGE,
            0L,
            LocalDateTime.of(2026, 6, 1, 9, 0));
    PointHistory midHistory =
        persistPointHistory(
            member,
            1_000L,
            PointTransactionType.POINT_CHARGE,
            PointReferenceType.POINT_CHARGE,
            0L,
            LocalDateTime.of(2026, 6, 2, 9, 0));
    PointHistory newHistory =
        persistPointHistory(
            member,
            1_000L,
            PointTransactionType.POINT_CHARGE,
            PointReferenceType.POINT_CHARGE,
            0L,
            LocalDateTime.of(2026, 6, 3, 9, 0));
    entityManager.flush();
    entityManager.clear();

    List<PointHistoryItemProjection> firstPage =
        pointHistoryQueryRepository.findHistoriesByCursor(member.getUuid(), 2, null, null);

    assertThat(firstPage).hasSize(2);
    assertThat(firstPage.get(0).pointHistoryId()).isEqualTo(newHistory.getId());
    assertThat(firstPage.get(1).pointHistoryId()).isEqualTo(midHistory.getId());

    List<PointHistoryItemProjection> secondPage =
        pointHistoryQueryRepository.findHistoriesByCursor(
            member.getUuid(), 2, firstPage.get(1).createdAt(), firstPage.get(1).pointHistoryId());

    assertThat(secondPage).hasSize(1);
    assertThat(secondPage.get(0).pointHistoryId()).isEqualTo(oldHistory.getId());
  }

  @Test
  void findHistoriesByCursorReturnsOnlyTargetMemberHistories() {
    Member member = persistMember("member@example.com", "회원");
    Member another = persistMember("other@example.com", "다른회원");
    persistPointHistory(
        member,
        1_000L,
        PointTransactionType.POINT_CHARGE,
        PointReferenceType.POINT_CHARGE,
        0L,
        LocalDateTime.of(2026, 6, 1, 10, 0));
    persistPointHistory(
        another,
        2_000L,
        PointTransactionType.POINT_CHARGE,
        PointReferenceType.POINT_CHARGE,
        0L,
        LocalDateTime.of(2026, 6, 1, 11, 0));

    List<PointHistoryItemProjection> result =
        pointHistoryQueryRepository.findHistoriesByCursor(member.getUuid(), 10, null, null);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).amount()).isEqualTo(1_000L);
  }

  @Test
  void findHistoriesByCursorAppliesTransactionTypeAndMonthFilters() {
    Member member = persistMember("filter-member@example.com", "필터회원");
    persistPointHistory(
        member,
        1_000L,
        PointTransactionType.POINT_CHARGE,
        PointReferenceType.POINT_CHARGE,
        0L,
        LocalDateTime.of(2026, 6, 1, 10, 0));
    persistPointHistory(
        member,
        -10_000L,
        PointTransactionType.CREW_DEPOSIT_RESERVE,
        PointReferenceType.CREW_PARTICIPANT,
        10L,
        LocalDateTime.of(2026, 6, 2, 10, 0));
    persistPointHistory(
        member,
        -10_000L,
        PointTransactionType.CREW_DEPOSIT_RESERVE,
        PointReferenceType.CREW_PARTICIPANT,
        11L,
        LocalDateTime.of(2026, 7, 1, 10, 0));
    entityManager.flush();
    entityManager.clear();

    YearMonth currentMonth = YearMonth.from(LocalDateTime.now());
    List<PointHistoryItemProjection> result =
        pointHistoryQueryRepository.findHistoriesByCursor(
            member.getUuid(),
            10,
            null,
            null,
            Set.of(PointTransactionType.CREW_DEPOSIT_RESERVE),
            currentMonth.atDay(1).atStartOfDay(),
            currentMonth.plusMonths(1).atDay(1).atStartOfDay());

    assertThat(result).hasSize(2);
    assertThat(result)
        .allMatch(item -> item.transactionType() == PointTransactionType.CREW_DEPOSIT_RESERVE);

    List<PointHistoryItemProjection> nextMonthResult =
        pointHistoryQueryRepository.findHistoriesByCursor(
            member.getUuid(),
            10,
            null,
            null,
            Set.of(PointTransactionType.CREW_DEPOSIT_RESERVE),
            currentMonth.plusMonths(1).atDay(1).atStartOfDay(),
            currentMonth.plusMonths(2).atDay(1).atStartOfDay());

    assertThat(nextMonthResult).isEmpty();
  }

  @Test
  void findCrewParticipantReferenceMetaReturnsCrewIdAndTitleForMemberParticipants() {
    Member member = persistMember("member@example.com", "회원");
    Crew crew = persistCrew(member, "참여 크루");
    CrewParticipant participant =
        persistCrewParticipant(member, crew, CrewParticipantStatus.LOCKED, 10_000L);
    persistPointHistory(
        member,
        -10_000L,
        PointTransactionType.CREW_DEPOSIT_RESERVE,
        PointReferenceType.CREW_PARTICIPANT,
        participant.getId(),
        LocalDateTime.of(2026, 6, 2, 10, 0));

    Map<Long, PointHistoryReferenceMetaProjection> metadata =
        pointHistoryQueryRepository.findCrewParticipantReferenceMeta(
            member.getUuid(), Set.of(participant.getId()));

    assertThat(metadata).hasSize(1);
    assertThat(metadata.get(participant.getId()).crewId()).isEqualTo(crew.getId());
    assertThat(metadata.get(participant.getId()).crewTitle()).isEqualTo("참여 크루");
  }

  @Test
  void findSettlementItemReferenceMetaReturnsCrewIdAndTitleForMemberSettlementItems() {
    Member member = persistMember("member@example.com", "회원");
    Crew crew = persistCrew(member, "정산 크루");
    CrewParticipant participant =
        persistCrewParticipant(member, crew, CrewParticipantStatus.LOCKED, 10_000L);
    Settlement settlement = persistSettlement(member, crew);
    SettlementItem settlementItem = persistSettlementItem(member, participant, settlement);

    Map<Long, PointHistoryReferenceMetaProjection> metadata =
        pointHistoryQueryRepository.findSettlementItemReferenceMeta(
            member.getUuid(), Set.of(settlementItem.getId()));

    assertThat(metadata).hasSize(1);
    assertThat(metadata.get(settlementItem.getId()).crewId()).isEqualTo(crew.getId());
    assertThat(metadata.get(settlementItem.getId()).crewTitle()).isEqualTo("정산 크루");
  }

  private Member persistMember(String email, String nickname) {
    return entityManager.persistAndFlush(Member.create(email, "password-hash", nickname));
  }

  private PointHistory persistPointHistory(
      Member member,
      long amount,
      PointTransactionType transactionType,
      PointReferenceType referenceType,
      Long referenceId,
      LocalDateTime createdAt) {
    String idempotencyKey = resolveIdempotencyKey(transactionType, referenceId);
    PointHistory history =
        PointHistory.create(
            member,
            amount,
            10_000L,
            0L,
            0L,
            transactionType,
            referenceType,
            referenceId,
            idempotencyKey);
    PointHistory persistedHistory = entityManager.persistAndFlush(history);
    ReflectionTestUtils.setField(persistedHistory, "createdAt", createdAt);
    return entityManager.persistAndFlush(persistedHistory);
  }

  private String resolveIdempotencyKey(PointTransactionType transactionType, Long referenceId) {
    String suffix = String.valueOf(++idempotencySequence);
    return switch (transactionType) {
      case POINT_CHARGE -> "charge:test-" + suffix;
      case CREW_DEPOSIT_RESERVE ->
          String.format("crew:1:participant:%d:reserve:%s", referenceId, suffix);
      case CREW_DEPOSIT_LOCK ->
          String.format("crew:1:participant:%d:reserve-lock:%s", referenceId, suffix);
      case CREW_RESERVE_RELEASE ->
          String.format("crew:1:participant:%d:reserve-release:%s", referenceId, suffix);
      case CREW_SETTLEMENT_REFUND -> "crew:1:participant:1:settlement-refund:final";
    };
  }

  private Crew persistCrew(Member host, String title) {
    Crew crew =
        Crew.create(
            host,
            title,
            title + " 설명",
            null,
            "OTHER",
            "{}",
            HostPolicyVersion.HOST_POLICY_V1,
            LocalDateTime.of(2026, 6, 1, 12, 0),
            10_000L,
            2,
            6,
            LocalDateTime.of(2026, 6, 9, 20, 0),
            LocalDateTime.of(2026, 6, 10, 0, 0),
            LocalDateTime.of(2026, 7, 10, 23, 59));
    return entityManager.persistAndFlush(crew);
  }

  private CrewParticipant persistCrewParticipant(
      Member member, Crew crew, CrewParticipantStatus status, Long depositAmount) {
    CrewParticipant participant =
        CrewParticipant.createPending(crew, member, depositAmount, LocalDateTime.now());
    ReflectionTestUtils.setField(participant, "status", status);
    return entityManager.persistAndFlush(participant);
  }

  private Settlement persistSettlement(Member host, Crew crew) {
    Settlement settlement = newSettlement();
    ReflectionTestUtils.setField(settlement, "crew", crew);
    ReflectionTestUtils.setField(settlement, "status", SettlementStatus.SUCCEEDED);
    ReflectionTestUtils.setField(
        settlement, "baselineFrozenAt", LocalDateTime.of(2026, 6, 5, 0, 0));
    ReflectionTestUtils.setField(settlement, "retryCount", 0);
    ReflectionTestUtils.setField(settlement, "totalParticipants", 1);
    ReflectionTestUtils.setField(settlement, "totalLockedAmount", 10_000L);
    ReflectionTestUtils.setField(settlement, "totalRecognizedSuccess", 1);
    ReflectionTestUtils.setField(settlement, "totalBaseRefundAmount", 1_000L);
    ReflectionTestUtils.setField(settlement, "totalRemainderAmount", 0L);
    ReflectionTestUtils.setField(settlement, "remainderPolicy", RemainderPolicy.HOST_REMAINDER);
    ReflectionTestUtils.setField(settlement, "algorithmVersion", "v1");
    ReflectionTestUtils.setField(settlement, "ruleContextSnapshot", "{}");
    return entityManager.persistAndFlush(settlement);
  }

  private SettlementItem persistSettlementItem(
      Member member, CrewParticipant participant, Settlement settlement) {
    SettlementItem settlementItem = newSettlementItem();
    ReflectionTestUtils.setField(settlementItem, "settlement", settlement);
    ReflectionTestUtils.setField(settlementItem, "crewParticipant", participant);
    ReflectionTestUtils.setField(settlementItem, "member", member);
    ReflectionTestUtils.setField(
        settlementItem, "participantStatusSnapshot", ParticipantStatusSnapshot.LOCKED);
    ReflectionTestUtils.setField(settlementItem, "depositAmount", 10_000L);
    ReflectionTestUtils.setField(settlementItem, "successCountRaw", 3);
    ReflectionTestUtils.setField(settlementItem, "recognizedSuccessCount", 3);
    ReflectionTestUtils.setField(settlementItem, "recognizedDatesCount", 3);
    ReflectionTestUtils.setField(settlementItem, "excludedSuccessCount", 0);
    ReflectionTestUtils.setField(
        settlementItem, "periodStartAt", LocalDateTime.of(2026, 6, 1, 0, 0));
    ReflectionTestUtils.setField(
        settlementItem, "periodEndAt", LocalDateTime.of(2026, 6, 30, 23, 59));
    ReflectionTestUtils.setField(settlementItem, "shareRatio", new BigDecimal("0.250000"));
    ReflectionTestUtils.setField(settlementItem, "baseRefundAmount", 1_000L);
    ReflectionTestUtils.setField(settlementItem, "remainderBonusAmount", 0L);
    ReflectionTestUtils.setField(settlementItem, "refundAmount", 1_000L);
    ReflectionTestUtils.setField(settlementItem, "effectiveModerationSnapshot", "{}");
    ReflectionTestUtils.setField(settlementItem, "moderationChainRef", "{}");
    ReflectionTestUtils.setField(settlementItem, "calculationReason", "{}");
    return entityManager.persistAndFlush(settlementItem);
  }

  private static Settlement newSettlement() {
    try {
      Constructor<Settlement> constructor = Settlement.class.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static SettlementItem newSettlementItem() {
    try {
      Constructor<SettlementItem> constructor = SettlementItem.class.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
