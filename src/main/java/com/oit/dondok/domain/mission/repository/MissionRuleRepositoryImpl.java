package com.oit.dondok.domain.mission.repository;

import static com.oit.dondok.domain.mission.entity.QMissionRule.missionRule;
import static com.oit.dondok.domain.mission.entity.QMissionScheduleDay.missionScheduleDay;

import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;

public class MissionRuleRepositoryImpl implements MissionRuleRepositoryCustom {

  private final JPAQueryFactory queryFactory;

  public MissionRuleRepositoryImpl(EntityManager entityManager) {
    this.queryFactory = new JPAQueryFactory(entityManager);
  }

  @Override
  public List<MissionRule> findActiveRulesForDailySettlement(
      DailySettlementType dailySettlementType,
      LocalDateTime missionDateStartInclusive,
      LocalDateTime missionDateEndExclusive,
      int dayOfWeek) {
    return queryFactory
        .selectFrom(missionRule)
        .join(missionRule.crew)
        .fetchJoin()
        .where(
            missionRule.dailySettlementType.eq(dailySettlementType),
            missionRule.crew.status.eq(CrewStatus.ACTIVE),
            missionRule.crew.startAt.lt(missionDateEndExclusive),
            missionRule.crew.endAt.goe(missionDateStartInclusive),
            matchesMissionDay(dayOfWeek))
        .orderBy(missionRule.crew.id.asc())
        .fetch();
  }

  private BooleanExpression matchesMissionDay(int dayOfWeek) {
    return missionRule.frequencyType.eq(MissionFrequencyType.DAILY).or(hasScheduleDay(dayOfWeek));
  }

  private BooleanExpression hasScheduleDay(int dayOfWeek) {
    return JPAExpressions.selectOne()
        .from(missionScheduleDay)
        .where(
            missionScheduleDay.missionRule.eq(missionRule),
            missionScheduleDay.dayOfWeek.eq(dayOfWeek))
        .exists();
  }
}
