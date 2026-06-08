package com.oit.dondok.domain.point.repository;

import static com.oit.dondok.domain.crew.entity.CrewParticipantStatus.LOCKED;
import static com.oit.dondok.domain.crew.entity.CrewStatus.ACTIVE;
import static com.oit.dondok.domain.crew.entity.CrewStatus.CLOSED;
import static com.oit.dondok.domain.crew.entity.CrewStatus.RECRUITING;
import static com.oit.dondok.domain.crew.entity.QCrew.crew;
import static com.oit.dondok.domain.crew.entity.QCrewParticipant.crewParticipant;
import static com.oit.dondok.domain.point.entity.QPointAccount.pointAccount;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
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

    NumberExpression<Long> settlementPendingAmountExpression =
        new CaseBuilder()
            .when(crewParticipant.status.eq(LOCKED).and(crew.status.eq(CLOSED)))
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
                    settlementPendingAmountExpression,
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

    return new PointBalanceProjection(
        zeroIfNull(account.availableBalance()),
        zeroIfNull(account.reservedBalance()),
        zeroIfNull(account.activeLockedAmount()),
        zeroIfNull(account.settlementPendingAmount()),
        zeroIfNull(account.lockedBalance()),
        account.updatedAt());
  }

  private static Long zeroIfNull(Long value) {
    return value == null ? 0L : value;
  }
}
