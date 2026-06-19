package com.oit.dondok.domain.point.repository;

import static com.oit.dondok.domain.crew.entity.QCrew.crew;
import static com.oit.dondok.domain.crew.entity.QCrewParticipant.crewParticipant;
import static com.oit.dondok.domain.member.entity.QMember.member;
import static com.oit.dondok.domain.point.entity.QPointHistory.pointHistory;
import static com.oit.dondok.domain.settlement.entity.QSettlementItem.settlementItem;

import com.oit.dondok.domain.point.entity.PointReferenceType;
import com.oit.dondok.domain.point.entity.PointTransactionType;
import com.oit.dondok.domain.point.entity.WalletHistoryDisplayType;
import com.oit.dondok.domain.point.entity.WalletHistoryStatus;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.Timestamp;
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
  private final EntityManager entityManager;

  public PointHistoryQueryRepository(JPAQueryFactory queryFactory, EntityManager entityManager) {
    this.queryFactory = queryFactory;
    this.entityManager = entityManager;
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
  public List<WalletHistoryEventProjection> findWalletHistoriesByCursor(
      UUID memberUuid,
      int limit,
      LocalDateTime cursorCreatedAt,
      String cursorWalletEventId,
      Set<WalletHistoryDisplayType> displayTypes,
      LocalDateTime monthStartInclusive,
      LocalDateTime monthEndExclusive) {
    Set<PointTransactionType> transactionTypes = walletTransactionTypes(displayTypes);
    if (transactionTypes != null && transactionTypes.isEmpty()) {
      return List.of();
    }
    if (transactionTypes == null) {
      transactionTypes =
          Set.of(
              PointTransactionType.POINT_CHARGE,
              PointTransactionType.CREW_DEPOSIT_RESERVE,
              PointTransactionType.CREW_DEPOSIT_LOCK,
              PointTransactionType.CREW_RESERVE_RELEASE,
              PointTransactionType.CREW_CANCEL_REFUND,
              PointTransactionType.CREW_SETTLEMENT_REFUND);
    }
    Long memberId =
        queryFactory.select(member.id).from(member).where(member.uuid.eq(memberUuid)).fetchOne();
    if (memberId == null) {
      return List.of();
    }
    StringBuilder sql = new StringBuilder(walletHistoryBaseSql(memberId));
    sql.append(" where 1 = 1");
    if (displayTypes != null) {
      sql.append(" and display_type in (");
      sql.append(
          toSqlLiteralList(displayTypes.stream().map(WalletHistoryDisplayType::name).toList()));
      sql.append(")");
    }
    if (monthStartInclusive != null && monthEndExclusive != null) {
      sql.append(" and created_at >= :rangeStart and created_at < :rangeEnd");
    }
    if (cursorCreatedAt != null && cursorWalletEventId != null) {
      sql.append(
          """
           and (
             created_at < :cursorCreatedAt
             or (created_at = :cursorCreatedAt and wallet_event_id < :cursorWalletEventId)
           )
          """);
    }
    sql.append(" order by created_at desc, wallet_event_id desc");

    Query query = entityManager.createNativeQuery(sql.toString());
    if (monthStartInclusive != null && monthEndExclusive != null) {
      query.setParameter("rangeStart", monthStartInclusive);
      query.setParameter("rangeEnd", monthEndExclusive);
    }
    if (cursorCreatedAt != null && cursorWalletEventId != null) {
      query.setParameter("cursorCreatedAt", cursorCreatedAt);
      query.setParameter("cursorWalletEventId", cursorWalletEventId);
    }
    query.setMaxResults(limit);

    @SuppressWarnings("unchecked")
    List<Object[]> rows = query.getResultList();
    return rows.stream().map(this::toWalletHistoryEventProjection).toList();
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

  private Set<PointTransactionType> walletTransactionTypes(
      Set<WalletHistoryDisplayType> displayTypes) {
    if (displayTypes == null) {
      return null;
    }
    return displayTypes.stream()
        .flatMap(
            displayType ->
                switch (displayType) {
                  case DODIN_CHARGE -> Set.of(PointTransactionType.POINT_CHARGE).stream();
                  case DODIN_DEPOSIT ->
                      Set.of(
                          PointTransactionType.CREW_DEPOSIT_RESERVE,
                          PointTransactionType.CREW_DEPOSIT_LOCK)
                          .stream();
                  case DODIN_DEPOSIT_REFUND ->
                      Set.of(
                          PointTransactionType.CREW_RESERVE_RELEASE,
                          PointTransactionType.CREW_CANCEL_REFUND)
                          .stream();
                  case SETTLEMENT_REFUND ->
                      Set.of(PointTransactionType.CREW_SETTLEMENT_REFUND).stream();
                  case DODIN_WITHDRAWAL -> Set.<PointTransactionType>of().stream();
                })
        .collect(Collectors.toSet());
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

  private String walletHistoryBaseSql(Long memberId) {
    return String.format(
        """
        with charge_events as (
          select
            concat('point-charge:', ph.id) as wallet_event_id,
            ph.amount,
            ph.available_after as balance_after,
            'DODIN_CHARGE' as display_type,
            'COMPLETED' as status,
            ph.reference_type,
            ph.reference_id,
            ph.created_at
          from point_history ph
          where ph.member_id = %d
            and ph.transaction_type = 'POINT_CHARGE'
        ),
        reserve_rows as (
          select
            ph.amount,
            ph.available_after,
            ph.reference_type,
            ph.reference_id,
            ph.created_at,
            1 as is_reserve,
            0 as is_lock
          from point_history ph
          where ph.member_id = %d
            and ph.transaction_type = 'CREW_DEPOSIT_RESERVE'
        ),
        lock_rows as (
          select
            ph.amount,
            ph.available_after,
            ph.reference_type,
            ph.reference_id,
            ph.created_at,
            0 as is_reserve,
            1 as is_lock
          from point_history ph
          where ph.member_id = %d
            and ph.transaction_type = 'CREW_DEPOSIT_LOCK'
        ),
        deposit_rows as (
          select * from reserve_rows
          union all
          select * from lock_rows
        ),
        deposit_events as (
          select
            concat('crew-deposit:', reference_id) as wallet_event_id,
            coalesce(
              max(case when is_reserve = 1 then amount end),
              max(case when is_lock = 1 then amount end)
            ) as amount,
            coalesce(
              max(case when is_reserve = 1 then available_after end),
              max(case when is_lock = 1 then available_after end)
            ) as balance_after,
            'DODIN_DEPOSIT' as display_type,
            case when max(is_lock) = 1 then 'CONFIRMED' else 'PENDING' end as status,
            max(reference_type) as reference_type,
            reference_id,
            coalesce(
              min(case when is_reserve = 1 then created_at end),
              min(case when is_lock = 1 then created_at end)
            ) as created_at
          from deposit_rows
          group by reference_id
        ),
        release_events as (
          select
            case
              when ph.transaction_type = 'CREW_CANCEL_REFUND' then concat('crew-cancel-refund:', ph.id)
              else concat('reserve-release:', ph.id)
            end as wallet_event_id,
            ph.amount,
            ph.available_after as balance_after,
            'DODIN_DEPOSIT_REFUND' as display_type,
            'RELEASED' as status,
            ph.reference_type,
            ph.reference_id,
            ph.created_at
          from point_history ph
          where ph.member_id = %d
            and ph.transaction_type in ('CREW_RESERVE_RELEASE', 'CREW_CANCEL_REFUND')
        ),
        settlement_events as (
          select
            concat('settlement-refund:', ph.reference_id) as wallet_event_id,
            ph.amount,
            ph.available_after as balance_after,
            'SETTLEMENT_REFUND' as display_type,
            'COMPLETED' as status,
            ph.reference_type,
            ph.reference_id,
            ph.created_at
          from point_history ph
          where ph.member_id = %d
            and ph.transaction_type = 'CREW_SETTLEMENT_REFUND'
        ),
        display_events as (
          select * from deposit_events
          union all
          select * from charge_events
          union all
          select * from release_events
          union all
          select * from settlement_events
        )
        select * from display_events
        """,
        memberId, memberId, memberId, memberId, memberId);
  }

  private WalletHistoryEventProjection toWalletHistoryEventProjection(Object[] row) {
    return new WalletHistoryEventProjection(
        (String) row[0],
        toLong(row[1]),
        toLong(row[2]),
        WalletHistoryDisplayType.valueOf((String) row[3]),
        WalletHistoryStatus.valueOf((String) row[4]),
        PointReferenceType.valueOf((String) row[5]),
        toLong(row[6]),
        toLocalDateTime(row[7]));
  }

  private Long toLong(Object value) {
    return ((Number) value).longValue();
  }

  private LocalDateTime toLocalDateTime(Object value) {
    if (value instanceof LocalDateTime localDateTime) {
      return localDateTime;
    }
    if (value instanceof Timestamp timestamp) {
      return timestamp.toLocalDateTime();
    }
    throw new IllegalStateException("Unsupported timestamp value: " + value);
  }

  private String toSqlLiteralList(List<String> values) {
    return values.stream().map(value -> "'" + value + "'").collect(Collectors.joining(", "));
  }
}
