package com.oit.dondok.domain.point.repository;

import static com.oit.dondok.domain.crew.entity.QCrew.crew;
import static com.oit.dondok.domain.crew.entity.QCrewParticipant.crewParticipant;
import static com.oit.dondok.domain.point.entity.QPointHistory.pointHistory;
import static com.oit.dondok.domain.settlement.entity.QSettlementItem.settlementItem;

import com.oit.dondok.domain.point.entity.PointTransactionType;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PointHistoryQueryRepository {

  private final JPAQueryFactory queryFactory;

  public PointHistoryQueryRepository(JPAQueryFactory queryFactory) {
    this.queryFactory = queryFactory;
  }

  @Transactional(readOnly = true)
  public List<PointHistoryItemProjection> findHistoriesByCursor(
      UUID memberUuid, int limit, LocalDateTime cursorCreatedAt, Long cursorId) {
    return findHistoriesByCursor(memberUuid, limit, cursorCreatedAt, cursorId, null, null, null);
  }

  @Transactional(readOnly = true)
  public List<PointHistoryItemProjection> findHistoriesByCursor(
      UUID memberUuid,
      int limit,
      LocalDateTime cursorCreatedAt,
      Long cursorId,
      Set<PointTransactionType> transactionTypes,
      LocalDateTime monthStartInclusive,
      LocalDateTime monthEndExclusive) {
    return queryFactory
        .select(
            Projections.constructor(
                PointHistoryItemProjection.class,
                pointHistory.id,
                pointHistory.amount,
                pointHistory.availableAfter,
                pointHistory.transactionType,
                pointHistory.referenceType,
                pointHistory.referenceId,
                pointHistory.createdAt))
        .from(pointHistory)
        .where(
            pointHistory.member.uuid.eq(memberUuid),
            buildTransactionTypeCondition(transactionTypes),
            buildMonthCondition(monthStartInclusive, monthEndExclusive),
            buildCursorCondition(cursorCreatedAt, cursorId))
        .orderBy(pointHistory.createdAt.desc(), pointHistory.id.desc())
        .limit(limit)
        .fetch();
  }

  @Transactional(readOnly = true)
  public Map<Long, PointHistoryReferenceMetaProjection> findCrewParticipantReferenceMeta(
      UUID memberUuid, Set<Long> participantIds) {
    if (participantIds.isEmpty()) {
      return Collections.emptyMap();
    }

    return queryFactory
        .select(
            Projections.constructor(
                PointHistoryReferenceMetaProjection.class, crewParticipant.id, crew.id, crew.title))
        .from(crewParticipant)
        .join(crewParticipant.crew, crew)
        .where(
            crewParticipant.member.uuid.eq(memberUuid).and(crewParticipant.id.in(participantIds)))
        .fetch()
        .stream()
        .collect(
            Collectors.toMap(
                PointHistoryReferenceMetaProjection::referenceId, meta -> meta, (a, b) -> a));
  }

  @Transactional(readOnly = true)
  public Map<Long, PointHistoryReferenceMetaProjection> findSettlementItemReferenceMeta(
      UUID memberUuid, Set<Long> settlementItemIds) {
    if (settlementItemIds.isEmpty()) {
      return Collections.emptyMap();
    }

    return queryFactory
        .select(
            Projections.constructor(
                PointHistoryReferenceMetaProjection.class, settlementItem.id, crew.id, crew.title))
        .from(settlementItem)
        .join(settlementItem.crewParticipant, crewParticipant)
        .join(crewParticipant.crew, crew)
        .where(
            settlementItem.member.uuid.eq(memberUuid).and(settlementItem.id.in(settlementItemIds)))
        .fetch()
        .stream()
        .collect(
            Collectors.toMap(
                PointHistoryReferenceMetaProjection::referenceId, meta -> meta, (a, b) -> a));
  }

  private Predicate buildTransactionTypeCondition(Set<PointTransactionType> transactionTypes) {
    if (transactionTypes == null) {
      return null;
    }
    return pointHistory.transactionType.in(transactionTypes);
  }

  private Predicate buildMonthCondition(
      LocalDateTime monthStartInclusive, LocalDateTime monthEndExclusive) {
    if (monthStartInclusive == null || monthEndExclusive == null) {
      return null;
    }
    return pointHistory
        .createdAt
        .goe(monthStartInclusive)
        .and(pointHistory.createdAt.lt(monthEndExclusive));
  }

  private Predicate buildCursorCondition(LocalDateTime cursorCreatedAt, Long cursorId) {
    if (cursorCreatedAt == null || cursorId == null) {
      return null;
    }

    return pointHistory
        .createdAt
        .lt(cursorCreatedAt)
        .or(pointHistory.createdAt.eq(cursorCreatedAt).and(pointHistory.id.lt(cursorId)));
  }
}
