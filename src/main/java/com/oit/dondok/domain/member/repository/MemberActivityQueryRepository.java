package com.oit.dondok.domain.member.repository;

import static com.oit.dondok.domain.crew.entity.QCrew.crew;
import static com.oit.dondok.domain.member.entity.QMember.member;
import static com.oit.dondok.domain.mission.entity.QMissionLog.missionLog;
import static com.oit.dondok.domain.notification.entity.QNotification.notification;
import static com.oit.dondok.domain.settlement.entity.QSettlement.settlement;
import static com.oit.dondok.domain.settlement.entity.QSettlementItem.settlementItem;

import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.entity.QCrewParticipant;
import com.oit.dondok.domain.settlement.entity.SettlementStatus;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MemberActivityQueryRepository {

  private final JPAQueryFactory queryFactory;

  public boolean existsByMemberUuid(UUID memberUuid) {
    return queryFactory.selectOne().from(member).where(member.uuid.eq(memberUuid)).fetchFirst()
        != null;
  }

  public CrewActivityInfoProjection findCrewActivityInfo(UUID memberUuid) {
    QCrewParticipant participant = new QCrewParticipant("crewActivityParticipant");

    long activeCrewCount =
        zeroIfNull(
            queryFactory
                .select(crew.id.countDistinct())
                .from(participant)
                .join(participant.crew, crew)
                .where(
                    participant.member.uuid.eq(memberUuid),
                    participant.status.eq(CrewParticipantStatus.LOCKED),
                    crew.status.in(CrewStatus.RECRUITING, CrewStatus.ACTIVE))
                .fetchOne());

    long completedCrewCount =
        zeroIfNull(
            queryFactory
                .select(crew.id.countDistinct())
                .from(participant)
                .join(participant.crew, crew)
                .where(
                    participant.member.uuid.eq(memberUuid),
                    participant.status.eq(CrewParticipantStatus.LOCKED),
                    crew.status.eq(CrewStatus.CLOSED))
                .fetchOne());

    return new CrewActivityInfoProjection(
        activeCrewCount + completedCrewCount, activeCrewCount, completedCrewCount);
  }

  public long countTotalVerification(UUID memberUuid) {
    QCrewParticipant participant = new QCrewParticipant("verificationParticipant");

    return zeroIfNull(
        queryFactory
            .select(missionLog.id.count())
            .from(missionLog)
            .join(missionLog.crewParticipant, participant)
            .where(participant.member.uuid.eq(memberUuid))
            .fetchOne());
  }

  public long countUnreadNotifications(UUID memberUuid) {
    return zeroIfNull(
        queryFactory
            .select(notification.id.count())
            .from(notification)
            .where(notification.member.uuid.eq(memberUuid), notification.readAt.isNull())
            .fetchOne());
  }

  public ActivityStatsProjection findActivityStats(UUID memberUuid) {
    long totalRecognizedSuccessCount = findTotalRecognizedSuccessCount(memberUuid);
    String averageSuccessRate = findAverageSuccessRate(memberUuid);

    Tuple highestShareRow =
        queryFactory
            .select(settlementItem.shareRatio, crew.id, crew.title)
            .from(settlementItem)
            .join(settlementItem.settlement, settlement)
            .join(settlement.crew, crew)
            .where(
                settlementItem.member.uuid.eq(memberUuid),
                settlement.status.eq(SettlementStatus.SUCCEEDED))
            .orderBy(settlementItem.shareRatio.desc(), settlement.finishedAt.desc(), crew.id.asc())
            .fetchFirst();

    if (highestShareRow == null) {
      return new ActivityStatsProjection(
          totalRecognizedSuccessCount, null, null, null, averageSuccessRate);
    }

    return new ActivityStatsProjection(
        totalRecognizedSuccessCount,
        highestShareRow.get(settlementItem.shareRatio),
        highestShareRow.get(crew.id),
        highestShareRow.get(crew.title),
        averageSuccessRate);
  }

  private long findTotalRecognizedSuccessCount(UUID memberUuid) {
    Integer sum =
        queryFactory
            .select(settlementItem.recognizedSuccessCount.sum())
            .from(settlementItem)
            .join(settlementItem.settlement, settlement)
            .where(
                settlementItem.member.uuid.eq(memberUuid),
                settlement.status.eq(SettlementStatus.SUCCEEDED))
            .fetchOne();
    return sum == null ? 0L : sum;
  }

  private String findAverageSuccessRate(UUID memberUuid) {
    Tuple row =
        queryFactory
            .select(settlementItem.recognizedSuccessCount.sum(), settlement.missionDays.sum())
            .from(settlementItem)
            .join(settlementItem.settlement, settlement)
            .where(
                settlementItem.member.uuid.eq(memberUuid),
                settlement.status.eq(SettlementStatus.SUCCEEDED),
                settlement.missionDays.gt(0))
            .fetchOne();

    if (row == null) {
      return null;
    }

    Integer successCount = row.get(settlementItem.recognizedSuccessCount.sum());
    Integer missionDays = row.get(settlement.missionDays.sum());
    if (successCount == null || missionDays == null) {
      return null;
    }

    return BigDecimal.valueOf(successCount)
        .divide(BigDecimal.valueOf(missionDays), 6, RoundingMode.FLOOR)
        .toPlainString();
  }

  private static long zeroIfNull(Long value) {
    return value == null ? 0L : value;
  }
}
