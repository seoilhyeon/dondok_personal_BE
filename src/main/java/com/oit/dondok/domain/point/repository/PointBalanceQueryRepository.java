package com.oit.dondok.domain.point.repository;

import static com.oit.dondok.domain.crew.entity.CrewParticipantStatus.LOCKED;
import static com.oit.dondok.domain.crew.entity.CrewStatus.ACTIVE;
import static com.oit.dondok.domain.crew.entity.CrewStatus.CLOSED;
import static com.oit.dondok.domain.crew.entity.CrewStatus.RECRUITING;
import static com.oit.dondok.domain.crew.entity.QCrew.crew;
import static com.oit.dondok.domain.crew.entity.QCrewParticipant.crewParticipant;
import static com.oit.dondok.domain.mission.entity.QMissionRule.missionRule;
import static com.oit.dondok.domain.point.entity.QPointAccount.pointAccount;
import static com.oit.dondok.domain.settlement.entity.DailySettlementPhase.FINALIZED;
import static com.oit.dondok.domain.settlement.entity.QDailySettlementParticipantSnapshot.dailySettlementParticipantSnapshot;
import static com.oit.dondok.domain.settlement.entity.QDailySettlementSnapshot.dailySettlementSnapshot;
import static com.oit.dondok.domain.settlement.entity.QSettlement.settlement;
import static com.oit.dondok.domain.settlement.entity.QSettlementItem.settlementItem;
import static com.oit.dondok.domain.settlement.entity.SettlementStatus.FAILED;
import static com.oit.dondok.domain.settlement.entity.SettlementStatus.PENDING;
import static com.oit.dondok.domain.settlement.entity.SettlementStatus.RETRY_WAIT;
import static com.oit.dondok.domain.settlement.entity.SettlementStatus.RUNNING;

import com.oit.dondok.domain.settlement.entity.DailySettlementStatus;
import com.oit.dondok.domain.settlement.entity.QDailySettlementSnapshot;
import com.oit.dondok.domain.settlement.entity.SettlementStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class PointBalanceQueryRepository {

  private final JPAQueryFactory queryFactory;

  @Transactional(readOnly = true)
  public PointBalanceProjection findWalletSummaryByMemberUuid(UUID memberUuid) {
    NumberExpression<Long> activeLockedAmountExpression =
        new CaseBuilder()
            .when(crewParticipant.status.eq(LOCKED).and(crew.status.in(RECRUITING, ACTIVE)))
            .then(crewParticipant.depositAmount)
            .otherwise(0L)
            .sum();

    PointBalanceProjection account =
        queryFactory
            .select(
                Projections.constructor(
                    PointBalanceProjection.class,
                    pointAccount.availableBalance,
                    pointAccount.reservedBalance,
                    activeLockedAmountExpression,
                    Expressions.constant(0L),
                    Expressions.constant(0L),
                    pointAccount.lockedBalance,
                    pointAccount.updatedAt))
            .from(pointAccount)
            .leftJoin(crewParticipant)
            .on(crewParticipant.member.eq(pointAccount.member))
            .leftJoin(crew)
            .on(crew.eq(crewParticipant.crew))
            .where(pointAccount.member.uuid.eq(memberUuid))
            .groupBy(
                pointAccount.id,
                pointAccount.availableBalance,
                pointAccount.reservedBalance,
                pointAccount.lockedBalance,
                pointAccount.updatedAt)
            .fetchOne();

    if (account == null) {
      return null;
    }

    Long settlementPendingAmount =
        zeroIfNull(
                findUnpaidSettlementRefundAmount(
                    memberUuid, java.util.List.of(PENDING, RUNNING, RETRY_WAIT)))
            + zeroIfNull(findPreSettlementExpectedRefundAmount(memberUuid));
    Long settlementFailedAmount =
        findUnpaidSettlementRefundAmount(memberUuid, java.util.List.of(FAILED));

    return new PointBalanceProjection(
        zeroIfNull(account.availableBalance()),
        zeroIfNull(account.reservedBalance()),
        zeroIfNull(account.activeLockedAmount()),
        zeroIfNull(settlementPendingAmount),
        zeroIfNull(settlementFailedAmount),
        zeroIfNull(account.lockedBalance()),
        account.updatedAt());
  }

  private Long findUnpaidSettlementRefundAmount(
      UUID memberUuid, Collection<SettlementStatus> statuses) {
    return queryFactory
        .select(settlementItem.refundAmount.sum())
        .from(settlementItem)
        .join(settlementItem.settlement, settlement)
        .where(
            settlementItem.member.uuid.eq(memberUuid),
            settlementItem.pointHistory.isNull(),
            settlement.status.in(statuses))
        .fetchOne();
  }

  private Long findPreSettlementExpectedRefundAmount(UUID memberUuid) {
    QDailySettlementSnapshot latestSnapshot = new QDailySettlementSnapshot("latestSnapshot");

    return queryFactory
        .select(dailySettlementParticipantSnapshot.expectedRefundAmount.sum())
        .from(dailySettlementParticipantSnapshot)
        .join(dailySettlementParticipantSnapshot.dailySettlementSnapshot, dailySettlementSnapshot)
        .join(dailySettlementSnapshot.crew, crew)
        .join(missionRule)
        .on(missionRule.crew.eq(crew))
        .leftJoin(settlement)
        .on(settlement.crew.eq(crew))
        .where(
            dailySettlementParticipantSnapshot.member.uuid.eq(memberUuid),
            dailySettlementParticipantSnapshot.crewParticipant.status.eq(LOCKED),
            crew.status.eq(CLOSED),
            settlement.id.isNull(),
            dailySettlementSnapshot.phase.eq(FINALIZED),
            dailySettlementSnapshot.status.eq(DailySettlementStatus.SUCCEEDED),
            dailySettlementSnapshot.dailySettlementType.eq(missionRule.dailySettlementType),
            dailySettlementSnapshot.missionDate.eq(
                JPAExpressions.select(latestSnapshot.missionDate.max())
                    .from(latestSnapshot)
                    .where(
                        latestSnapshot.crew.eq(crew),
                        latestSnapshot.dailySettlementType.eq(missionRule.dailySettlementType),
                        latestSnapshot.phase.eq(FINALIZED),
                        latestSnapshot.status.eq(DailySettlementStatus.SUCCEEDED))))
        .fetchOne();
  }

  private static Long zeroIfNull(Long value) {
    return value == null ? 0L : value;
  }
}
