package com.oit.dondok.domain.dashboard.repository;

import static com.oit.dondok.domain.settlement.entity.QDailySettlementParticipantSnapshot.dailySettlementParticipantSnapshot;
import static com.oit.dondok.domain.settlement.entity.QDailySettlementSnapshot.dailySettlementSnapshot;

import com.oit.dondok.domain.settlement.entity.DailySettlementPhase;
import com.oit.dondok.domain.settlement.entity.DailySettlementStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

// PROVISIONAL, SUCCEEDED 스냅샷에서 회원의 projection 행을 crew별 mission_date 내림차순으로 조회한다.
// 회원이 해당 스냅샷에 참여 행이 없으면 LEFT JOIN으로 projection 값이 null인 행을 반환한다(배치 존재 여부 보존).
@Repository
@RequiredArgsConstructor
public class DashboardProjectionQueryRepository {
  private final JPAQueryFactory queryFactory;

  public List<DashboardProjectionRow> findProvisionalProjectionRows(
      Long memberId, List<Long> crewIds) {
    if (crewIds.isEmpty()) {
      return List.of();
    }
    return queryFactory
        .select(
            Projections.constructor(
                DashboardProjectionRow.class,
                dailySettlementSnapshot.crew.id,
                dailySettlementSnapshot.missionDate,
                dailySettlementSnapshot.frozenAt,
                dailySettlementParticipantSnapshot.shareRatio,
                dailySettlementParticipantSnapshot.expectedRefundAmount))
        .from(dailySettlementSnapshot)
        .leftJoin(dailySettlementParticipantSnapshot)
        .on(
            dailySettlementParticipantSnapshot
                .dailySettlementSnapshot
                .id
                .eq(dailySettlementSnapshot.id)
                .and(dailySettlementParticipantSnapshot.member.id.eq(memberId)))
        .where(
            dailySettlementSnapshot.crew.id.in(crewIds),
            dailySettlementSnapshot.phase.eq(DailySettlementPhase.PROVISIONAL),
            dailySettlementSnapshot.status.eq(DailySettlementStatus.SUCCEEDED))
        .orderBy(
            dailySettlementSnapshot.crew.id.asc(),
            dailySettlementSnapshot.missionDate.desc(),
            dailySettlementSnapshot.frozenAt.desc())
        .fetch();
  }
}
