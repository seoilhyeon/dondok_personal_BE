package com.oit.dondok.domain.dashboard.repository;

import static com.oit.dondok.domain.member.entity.QMember.member;
import static com.oit.dondok.domain.settlement.entity.QDailySettlementParticipantSnapshot.dailySettlementParticipantSnapshot;
import static com.oit.dondok.domain.settlement.entity.QDailySettlementSnapshot.dailySettlementSnapshot;

import com.oit.dondok.domain.settlement.entity.DailySettlementPhase;
import com.oit.dondok.domain.settlement.entity.DailySettlementStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

// 크루 상세 대시보드용 조회. PROVISIONAL·SUCCEEDED 스냅샷만 본다.
@Repository
@RequiredArgsConstructor
public class CrewDashboardQueryRepository {

  private final JPAQueryFactory queryFactory;

  // 크루의 최신 PROVISIONAL 스냅샷을 mission_date 내림차순으로 limit개 조회 (최신=0, 직전=1).
  public List<CrewDashboardSnapshotRow> findRecentProvisionalSnapshots(Long crewId, int limit) {
    return queryFactory
        .select(
            Projections.constructor(
                CrewDashboardSnapshotRow.class,
                dailySettlementSnapshot.id,
                dailySettlementSnapshot.missionDate,
                dailySettlementSnapshot.frozenAt))
        .from(dailySettlementSnapshot)
        .where(
            dailySettlementSnapshot.crew.id.eq(crewId),
            dailySettlementSnapshot.phase.eq(DailySettlementPhase.PROVISIONAL),
            dailySettlementSnapshot.status.eq(DailySettlementStatus.SUCCEEDED))
        .orderBy(
            dailySettlementSnapshot.missionDate.desc(), dailySettlementSnapshot.frozenAt.desc())
        .limit(limit)
        .fetch();
  }

  // 주어진 스냅샷들의 모든 참여자 행을 crew_participant_id 오름차순으로 조회.
  public List<CrewDashboardParticipantRow> findParticipantRows(List<Long> snapshotIds) {
    if (snapshotIds.isEmpty()) {
      return List.of();
    }
    return queryFactory
        .select(
            Projections.constructor(
                CrewDashboardParticipantRow.class,
                dailySettlementParticipantSnapshot.dailySettlementSnapshot.id,
                dailySettlementParticipantSnapshot.crewParticipant.id,
                dailySettlementParticipantSnapshot.member.id,
                member.nickname,
                dailySettlementParticipantSnapshot.successCount,
                dailySettlementParticipantSnapshot.shareRatio,
                dailySettlementParticipantSnapshot.expectedRefundAmount))
        .from(dailySettlementParticipantSnapshot)
        .join(dailySettlementParticipantSnapshot.member, member)
        .where(dailySettlementParticipantSnapshot.dailySettlementSnapshot.id.in(snapshotIds))
        .orderBy(dailySettlementParticipantSnapshot.crewParticipant.id.asc())
        .fetch();
  }
}
