package com.oit.dondok.domain.settlement.repository;

import static com.oit.dondok.domain.crew.entity.QCrew.crew;
import static com.oit.dondok.domain.crew.entity.QCrewParticipant.crewParticipant;
import static com.oit.dondok.domain.point.entity.QPointHistory.pointHistory;
import static com.oit.dondok.domain.settlement.entity.QSettlement.settlement;
import static com.oit.dondok.domain.settlement.entity.QSettlementItem.settlementItem;

import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.member.entity.QMember;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SettlementQueryRepository {

  private final JPAQueryFactory queryFactory;

  public Optional<Settlement> findAccessibleByIdAndMemberUuid(Long settlementId, UUID memberUuid) {
    QMember hostMember = new QMember("hostMember");

    return Optional.ofNullable(
        queryFactory
            .select(settlement)
            .from(settlement)
            .join(settlement.crew, crew)
            .fetchJoin()
            .join(crew.hostMember, hostMember)
            .fetchJoin()
            .leftJoin(crewParticipant)
            .on(
                crewParticipant
                    .crew
                    .eq(crew)
                    .and(crewParticipant.member.uuid.eq(memberUuid))
                    .and(crewParticipant.status.eq(CrewParticipantStatus.LOCKED)))
            .where(
                settlement
                    .id
                    .eq(settlementId)
                    .and(hostMember.uuid.eq(memberUuid).or(crewParticipant.id.isNotNull())))
            .fetchOne());
  }

  public Optional<SettlementMeProjection> findSettlementMeByIdAndMemberUuid(
      Long settlementId, UUID memberUuid) {
    QMember hostMember = new QMember("hostMember");

    return Optional.ofNullable(
        queryFactory
            .select(
                Projections.constructor(
                    SettlementMeProjection.class,
                    settlement.id,
                    crew.id,
                    settlement.crewName,
                    settlement.crewStartedAt,
                    settlement.crewEndedAt,
                    settlement.status,
                    settlement.retryCount,
                    settlement.failureCode,
                    settlement.failureMessage,
                    settlement.startedAt,
                    settlement.finishedAt,
                    settlementItem.id,
                    settlementItem.crewParticipant.id,
                    settlementItem.participantStatusSnapshot,
                    settlementItem.depositAmount,
                    settlementItem.successCountRaw,
                    settlementItem.recognizedSuccessCount,
                    settlementItem.recognizedDatesCount,
                    settlementItem.excludedSuccessCount,
                    settlementItem.shareRatio,
                    settlementItem.baseRefundAmount,
                    settlementItem.remainderBonusAmount,
                    settlementItem.refundAmount,
                    pointHistory.id,
                    settlementItem.calculationReason))
            .from(settlement)
            .join(settlement.crew, crew)
            .join(crew.hostMember, hostMember)
            .leftJoin(crewParticipant)
            .on(
                crewParticipant
                    .crew
                    .eq(crew)
                    .and(crewParticipant.member.uuid.eq(memberUuid))
                    .and(crewParticipant.status.eq(CrewParticipantStatus.LOCKED)))
            .leftJoin(settlementItem)
            .on(
                settlementItem
                    .settlement
                    .eq(settlement)
                    .and(settlementItem.member.uuid.eq(memberUuid)))
            .leftJoin(pointHistory)
            .on(pointHistory.eq(settlementItem.pointHistory))
            .where(
                settlement
                    .id
                    .eq(settlementId)
                    .and(hostMember.uuid.eq(memberUuid).or(crewParticipant.id.isNotNull())))
            .fetchOne());
  }
}
