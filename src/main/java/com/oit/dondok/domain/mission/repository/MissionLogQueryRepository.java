package com.oit.dondok.domain.mission.repository;

import static com.oit.dondok.domain.crew.entity.QCrew.crew;
import static com.oit.dondok.domain.crew.entity.QCrewParticipant.crewParticipant;
import static com.oit.dondok.domain.member.entity.QMember.member;
import static com.oit.dondok.domain.mission.entity.QMissionLog.missionLog;
import static com.oit.dondok.domain.mission.entity.QMissionRule.missionRule;
import static com.oit.dondok.domain.settlement.entity.QSettlement.settlement;

import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.mission.entity.CertificationStatus;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionLog;
import com.querydsl.core.types.dsl.BooleanExpression;
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
            JPAExpressions.selectOne()
                .from(settlement)
                .where(settlement.crew.id.eq(crew.id))
                .notExists())
        .orderBy(missionLog.serverTime.asc(), missionLog.id.asc())
        .limit(limit)
        .fetch();
  }

  // 자동 인증 직전 최신 상태를 확인하기 위해 대상 로그를 쓰기 잠금으로 조회한다.
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
}
