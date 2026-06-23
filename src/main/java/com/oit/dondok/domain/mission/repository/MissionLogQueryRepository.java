package com.oit.dondok.domain.mission.repository;

import static com.oit.dondok.domain.crew.entity.QCrew.crew;
import static com.oit.dondok.domain.crew.entity.QCrewParticipant.crewParticipant;
import static com.oit.dondok.domain.member.entity.QMember.member;
import static com.oit.dondok.domain.mission.entity.QMissionLog.missionLog;
import static com.oit.dondok.domain.mission.entity.QMissionRule.missionRule;
import static com.oit.dondok.domain.mission.entity.QMissionScheduleDay.missionScheduleDay;
import static com.oit.dondok.domain.settlement.entity.QSettlement.settlement;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.entity.QCrewParticipant;
import com.oit.dondok.domain.member.entity.QMember;
import com.oit.dondok.domain.mission.entity.CertificationStatus;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.ExifRisk;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.mission.entity.MissionLog;
import com.oit.dondok.domain.mission.entity.MissionLogReviewBucket;
import com.oit.dondok.domain.mission.entity.ModerationDecisionType;
import com.oit.dondok.domain.mission.entity.QMissionLog;
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
    QMember submitter = new QMember("submitter");
    return Optional.ofNullable(
        queryFactory
            .selectFrom(missionLog)
            .join(missionLog.crewParticipant, crewParticipant)
            .fetchJoin()
            .join(crewParticipant.crew, crew)
            .fetchJoin()
            .join(crew.hostMember, member)
            .fetchJoin()
            .join(crewParticipant.member, submitter)
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
            reviewWindowCondition(bucket, now),
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
                reviewWindowCondition(bucket, now),
                noSettlementExists())
            .fetchOne();
    return count == null ? 0L : count;
  }

  // 미검토 인증 알림 대상: 슬롯 내 PENDING_REVIEW 인증이 있는 크루의 방장 (cursor-based)
  public List<Crew> findUnreviewedHostTargets(
      DailySettlementType settlementType,
      LocalDateTime slotStart,
      LocalDateTime slotEnd,
      long cursorId,
      int limit) {
    QCrewParticipant cpAlias = new QCrewParticipant("cpAlias");
    QMissionLog mlAlias = new QMissionLog("mlAlias");
    return queryFactory
        .selectFrom(crew)
        .join(crew.hostMember, member)
        .fetchJoin()
        .join(missionRule)
        .on(missionRule.crew.id.eq(crew.id))
        .where(
            crew.status.eq(CrewStatus.ACTIVE),
            missionRule.dailySettlementType.eq(settlementType),
            noSettlementExists(),
            hasPendingReviewInSlot(cpAlias, mlAlias, slotStart, slotEnd),
            crew.id.gt(cursorId))
        .orderBy(crew.id.asc())
        .limit(limit)
        .fetch();
  }

  // 인증 마감 임박 알림 대상: 특정 타입 크루에서 오늘 아직 인증하지 않은 LOCKED 참여자.
  // SPECIFIC_DAYS 크루는 오늘 요일이 미션 스케줄에 포함될 때만 대상에 포함한다.
  public List<CrewParticipant> findDeadlineReminderTargets(
      DailySettlementType settlementType,
      LocalDateTime todayStart,
      LocalDateTime todayEnd,
      int dayOfWeek,
      long cursorId,
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
            isMissionDay(dayOfWeek),
            noSettlementExists(),
            noCertificationToday(todayStart, todayEnd),
            crewParticipant.id.gt(cursorId))
        .orderBy(crewParticipant.id.asc())
        .limit(limit)
        .fetch();
  }

  private BooleanExpression isMissionDay(int dayOfWeek) {
    return missionRule
        .frequencyType
        .eq(MissionFrequencyType.DAILY)
        .or(
            JPAExpressions.selectOne()
                .from(missionScheduleDay)
                .where(
                    missionScheduleDay.missionRule.eq(missionRule),
                    missionScheduleDay.dayOfWeek.eq(dayOfWeek))
                .exists());
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
      case DECIDED ->
          missionLog
              .certificationStatus
              .eq(CertificationStatus.SUCCESS)
              .and(missionLog.decisionType.eq(ModerationDecisionType.MANUAL_APPROVE))
              .or(
                  missionLog
                      .certificationStatus
                      .eq(CertificationStatus.FAILED)
                      .and(missionLog.decisionType.eq(ModerationDecisionType.MANUAL_REJECT)));
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

  // DECIDED: 수동 결정은 다음 배치 시각까지, 자동 결정은 hostReviewableUntil까지 표시한다.
  // PENDING_REVIEW 버킷: hostReviewableUntil 이내인 항목만 표시한다.
  private BooleanExpression reviewWindowCondition(
      MissionLogReviewBucket bucket, LocalDateTime now) {
    if (bucket == MissionLogReviewBucket.DECIDED) {
      return missionLog
          .decisionType
          .in(ModerationDecisionType.MANUAL_APPROVE, ModerationDecisionType.MANUAL_REJECT)
          .and(nextBatchAtExpression().goe(now))
          .or(
              missionLog
                  .decisionType
                  .in(ModerationDecisionType.AUTO_APPROVE, ModerationDecisionType.AUTO_REJECT)
                  .and(hostReviewableUntilExpression().goe(now)));
    }
    return hostReviewableUntilExpression().goe(now);
  }

  // 다음 스냅샷 배치 실행 시각 (A: +36h, B: +48h, C: +60h).
  // 수동 결정 항목을 다음 배치가 반영하는 시점까지만 DECIDED 버킷에 노출한다.
  private DateTimeExpression<LocalDateTime> nextBatchAtExpression() {
    NumberExpression<Integer> hours =
        new CaseBuilder()
            .when(missionRule.dailySettlementType.eq(DailySettlementType.A))
            .then(36)
            .when(missionRule.dailySettlementType.eq(DailySettlementType.B))
            .then(48)
            .otherwise(60);

    return Expressions.dateTimeTemplate(
        LocalDateTime.class,
        "timestampadd(hour, {0}, cast(cast({1} as date) as LocalDateTime))",
        hours,
        missionLog.serverTime);
  }

  // 자동 판정 예정 시각에 72시간 유예기간을 더한 시각을 SQL 표현식으로 만든다.
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
        "timestampadd(hour, {0}, cast(cast({1} as date) as LocalDateTime))",
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

  // 슬롯 내 LOCKED 참여자의 PENDING_REVIEW 인증이 하나라도 있는 크루만 남기기 위한 EXISTS 조건
  private BooleanExpression hasPendingReviewInSlot(
      QCrewParticipant cpAlias,
      QMissionLog mlAlias,
      LocalDateTime slotStart,
      LocalDateTime slotEnd) {
    return JPAExpressions.selectOne()
        .from(mlAlias)
        .join(cpAlias)
        .on(cpAlias.id.eq(mlAlias.crewParticipant.id))
        .where(
            cpAlias.crew.id.eq(crew.id),
            cpAlias.status.eq(CrewParticipantStatus.LOCKED),
            mlAlias.certificationStatus.eq(CertificationStatus.PENDING_REVIEW),
            mlAlias.serverTime.goe(slotStart),
            mlAlias.serverTime.lt(slotEnd))
        .exists();
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
