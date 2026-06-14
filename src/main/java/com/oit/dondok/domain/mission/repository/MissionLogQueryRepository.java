package com.oit.dondok.domain.mission.repository;

import static com.oit.dondok.domain.crew.entity.QCrew.crew;
import static com.oit.dondok.domain.crew.entity.QCrewParticipant.crewParticipant;
import static com.oit.dondok.domain.member.entity.QMember.member;
import static com.oit.dondok.domain.mission.entity.QMissionLog.missionLog;
import static com.oit.dondok.domain.mission.entity.QMissionRule.missionRule;
import static com.oit.dondok.domain.settlement.entity.QSettlement.settlement;

import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.mission.entity.CertificationStatus;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.ExifRisk;
import com.oit.dondok.domain.mission.entity.MissionLog;
import com.oit.dondok.domain.mission.entity.MissionLogReviewBucket;
import com.oit.dondok.domain.mission.entity.ModerationDecisionType;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MissionLogQueryRepository {

  private final JPAQueryFactory queryFactory;

  public Optional<MissionLog> findByIdWithCrewForModeration(Long missionLogId) {
    return Optional.ofNullable(
        queryFactory
            .selectFrom(missionLog)
            .join(missionLog.crewParticipant, crewParticipant)
            .fetchJoin()
            .join(crewParticipant.crew, crew)
            .fetchJoin()
            .join(crew.hostMember, member)
            .fetchJoin()
            .where(missionLog.id.eq(missionLogId))
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .fetchOne());
  }

  public List<Long> findAutoCertificationCandidateIds(LocalDateTime now, int limit) {
    return queryFactory
        .select(missionLog.id)
        .from(missionLog)
        .join(missionLog.crewParticipant, crewParticipant)
        .join(crewParticipant.crew, crew)
        .join(missionRule)
        .on(missionRule.crew.id.eq(crew.id))
        .where(
            missionLog.certificationStatus.eq(CertificationStatus.PENDING_REVIEW),
            crew.status.eq(CrewStatus.ACTIVE),
            crewParticipant.status.eq(CrewParticipantStatus.LOCKED),
            autoCertificationDue(now),
            noSettlementExists())
        .orderBy(missionLog.serverTime.asc(), missionLog.id.asc())
        .limit(limit)
        .fetch();
  }

  // 자동 인증 직전 최신 상태를 확인하기 위해 대상 로그를 잠금으로 조회한다.
  public Optional<MissionLog> findByIdWithCrewForAutoCertification(Long missionLogId) {
    return Optional.ofNullable(
        queryFactory
            .selectFrom(missionLog)
            .join(missionLog.crewParticipant, crewParticipant)
            .fetchJoin()
            .join(crewParticipant.crew, crew)
            .fetchJoin()
            .where(missionLog.id.eq(missionLogId))
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .fetchOne());
  }

  // 방장 검토 목록 계산에 필요한 인증, 크루, 참여자, 회원 정보를 한 번에 조회한다.
  public List<MissionLog> findReviewablePageByCrewId(
      Long crewId,
      MissionLogReviewBucket bucket,
      LocalDateTime cursorSortTime,
      Long cursorMissionLogId,
      int limit,
      LocalDateTime now) {
    DateTimeExpression<LocalDateTime> sortTime = reviewSortTime(bucket);
    return queryFactory
        .selectFrom(missionLog)
        .join(missionLog.crewParticipant, crewParticipant)
        .fetchJoin()
        .join(crewParticipant.crew, crew)
        .fetchJoin()
        .join(crewParticipant.member, member)
        .fetchJoin()
        .join(missionRule)
        .on(missionRule.crew.id.eq(crew.id))
        .where(
            crew.id.eq(crewId),
            crew.status.eq(CrewStatus.ACTIVE),
            crewParticipant.status.eq(CrewParticipantStatus.LOCKED),
            reviewableDecisionState(bucket),
            hostReviewableUntilExpression().goe(now),
            noSettlementExists(),
            cursorCondition(sortTime, cursorSortTime, cursorMissionLogId))
        .orderBy(sortTime.asc(), missionLog.id.asc())
        .limit(limit)
        .fetch();
  }

  // 방장 검토 가능한 인증 수를 bucket 기준으로 DB에서 집계한다.
  public long countReviewableByCrewIdAndBucket(
      Long crewId, MissionLogReviewBucket bucket, LocalDateTime now) {
    Long count =
        queryFactory
            .select(missionLog.count())
            .from(missionLog)
            .join(missionLog.crewParticipant, crewParticipant)
            .join(crewParticipant.crew, crew)
            .join(missionRule)
            .on(missionRule.crew.id.eq(crew.id))
            .where(
                crew.id.eq(crewId),
                crew.status.eq(CrewStatus.ACTIVE),
                crewParticipant.status.eq(CrewParticipantStatus.LOCKED),
                reviewableDecisionState(bucket),
                hostReviewableUntilExpression().goe(now),
                noSettlementExists())
            .fetchOne();
    return count == null ? 0L : count;
  }

  // 인증 마감 임박 알림 대상: 특정 타입 크루에서 오늘 아직 인증하지 않은 LOCKED 참여자
  public List<CrewParticipant> findDeadlineReminderTargets(
      DailySettlementType settlementType,
      LocalDateTime todayStart,
      LocalDateTime todayEnd,
      int limit) {
    return queryFactory
        .selectFrom(crewParticipant)
        .join(crewParticipant.crew, crew)
        .fetchJoin()
        .join(crewParticipant.member, member)
        .fetchJoin()
        .join(missionRule)
        .on(missionRule.crew.id.eq(crew.id))
        .where(
            crew.status.eq(CrewStatus.ACTIVE),
            crewParticipant.status.eq(CrewParticipantStatus.LOCKED),
            missionRule.dailySettlementType.eq(settlementType),
            noSettlementExists(),
            noCertificationToday(todayStart, todayEnd))
        .limit(limit)
        .fetch();
  }

  // bucket별 검토 대상 상태와 위험 신호 조건을 만든다.
  private BooleanExpression reviewableDecisionState(MissionLogReviewBucket bucket) {
    return switch (bucket) {
      case URGENT ->
          missionLog
              .certificationStatus
              .eq(CertificationStatus.SUCCESS)
              .and(missionLog.decisionType.eq(ModerationDecisionType.AUTO_APPROVE))
              .or(
                  missionLog
                      .certificationStatus
                      .eq(CertificationStatus.FAILED)
                      .and(missionLog.decisionType.eq(ModerationDecisionType.AUTO_REJECT)));
      case WARNING ->
          pendingReviewWithoutDecision()
              .and(
                  missionLog
                      .exifRisk
                      .in(ExifRisk.MISSING, ExifRisk.TIME_INVALID)
                      .or(missionLog.duplicateHash.isTrue()));
      case NORMAL ->
          pendingReviewWithoutDecision()
              .and(missionLog.exifRisk.eq(ExifRisk.NORMAL))
              .and(missionLog.duplicateHash.isFalse());
    };
  }

  // 시스템 자동 판정 전인 방장 검토 대기 상태를 표현한다.
  private BooleanExpression pendingReviewWithoutDecision() {
    return missionLog
        .certificationStatus
        .eq(CertificationStatus.PENDING_REVIEW)
        .and(missionLog.decisionType.isNull());
  }

  // 정산이 시작된 크루는 자동 판정/방장 검토 대상에서 제외한다.
  private BooleanExpression noSettlementExists() {
    return JPAExpressions.selectOne()
        .from(settlement)
        .where(settlement.crew.id.eq(crew.id))
        .notExists();
  }

  // bucket별 cursor 정렬 기준을 만든다.
  private DateTimeExpression<LocalDateTime> reviewSortTime(MissionLogReviewBucket bucket) {
    if (bucket == MissionLogReviewBucket.URGENT) {
      return hostReviewableUntilExpression();
    }
    return missionLog.serverTime;
  }

  // 정책상 자동 판정 예정 시각에 72시간 유예기간을 더한 시각을 SQL 표현식으로 만든다.
  private DateTimeExpression<LocalDateTime> hostReviewableUntilExpression() {
    NumberExpression<Integer> reviewableHours =
        new CaseBuilder()
            .when(missionRule.dailySettlementType.eq(DailySettlementType.A))
            .then(84)
            .when(missionRule.dailySettlementType.eq(DailySettlementType.B))
            .then(96)
            .otherwise(108);

    return Expressions.dateTimeTemplate(
        LocalDateTime.class,
        "timestampadd(hour, {0}, cast(date({1}) as LocalDateTime))",
        reviewableHours,
        missionLog.serverTime);
  }

  // keyset cursor 이후의 데이터만 조회하도록 조건을 만든다.
  private BooleanExpression cursorCondition(
      DateTimeExpression<LocalDateTime> sortTime,
      LocalDateTime cursorSortTime,
      Long cursorMissionLogId) {
    if (cursorSortTime == null || cursorMissionLogId == null) {
      return null;
    }
    return sortTime
        .gt(cursorSortTime)
        .or(sortTime.eq(cursorSortTime).and(missionLog.id.gt(cursorMissionLogId)));
  }

  // 타입별 자동 인증 시각이 지난 로그만 후보로 남기기 위한 DB 필터다.
  private BooleanExpression autoCertificationDue(LocalDateTime now) {
    LocalDate today = now.toLocalDate();

    LocalDateTime typeACutoffExclusive =
        (now.toLocalTime().isBefore(LocalTime.NOON) ? today : today.plusDays(1)).atStartOfDay();
    LocalDateTime typeBCutoffExclusive = today.atStartOfDay();
    LocalDateTime typeCCutoffExclusive =
        (now.toLocalTime().isBefore(LocalTime.NOON) ? today.minusDays(1) : today).atStartOfDay();

    return missionRule
        .dailySettlementType
        .eq(DailySettlementType.A)
        .and(missionLog.serverTime.lt(typeACutoffExclusive))
        .or(
            missionRule
                .dailySettlementType
                .eq(DailySettlementType.B)
                .and(missionLog.serverTime.lt(typeBCutoffExclusive)))
        .or(
            missionRule
                .dailySettlementType
                .eq(DailySettlementType.C)
                .and(missionLog.serverTime.lt(typeCCutoffExclusive)));
  }

  // 오늘 인증 제출(SUCCESS 또는 PENDING_REVIEW)이 없는 참여자만 남기기 위한 NOT EXISTS 조건
  private BooleanExpression noCertificationToday(LocalDateTime todayStart, LocalDateTime todayEnd) {
    return JPAExpressions.selectOne()
        .from(missionLog)
        .where(
            missionLog.crewParticipant.id.eq(crewParticipant.id),
            missionLog.certificationStatus.in(
                CertificationStatus.SUCCESS, CertificationStatus.PENDING_REVIEW),
            missionLog.serverTime.goe(todayStart),
            missionLog.serverTime.lt(todayEnd))
        .notExists();
  }
}
