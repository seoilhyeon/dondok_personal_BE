package com.oit.dondok.domain.mission.repository;

import static com.oit.dondok.domain.crew.entity.QCrew.crew;
import static com.oit.dondok.domain.crew.entity.QCrewParticipant.crewParticipant;
import static com.oit.dondok.domain.mission.entity.QMissionLog.missionLog;
import static com.oit.dondok.domain.mission.entity.QMissionLogReaction.missionLogReaction;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

// 리액션 인가/삭제용 QueryDSL 쿼리. JPQL 문자열 직접 작성 금지 가이드라인을 준수한다.
// 멱등 추가(upsert)는 MySQL ON DUPLICATE KEY UPDATE라 QueryDSL/JPQL로 표현 불가하므로
// MissionLogReactionRepository의 native query로 남겨둔다.
@Repository
@RequiredArgsConstructor
public class MissionLogReactionQueryRepository {

  private final JPAQueryFactory queryFactory;

  public record MissionLogOwnerContext(Long ownerMemberId, Long crewId) {}

  // 로그 소유자 memberId + crewId를 한 쿼리로 반환. 결과 없음 = 로그 부재(MISSION_LOG_NOT_FOUND 판정).
  // crewParticipant.member.id는 crew_participant.member_id FK 컬럼 직접 접근 — member 테이블 JOIN 없음.
  public Optional<MissionLogOwnerContext> findOwnerContext(Long missionLogId) {
    Tuple result =
        queryFactory
            .select(crewParticipant.member.id, crew.id)
            .from(missionLog)
            .join(missionLog.crewParticipant, crewParticipant)
            .join(crewParticipant.crew, crew)
            .where(missionLog.id.eq(missionLogId))
            .fetchOne();
    if (result == null) {
      return Optional.empty();
    }
    return Optional.of(
        new MissionLogOwnerContext(result.get(crewParticipant.member.id), result.get(crew.id)));
  }

  // 멱등 삭제: 매칭 row가 없어도 0건 삭제로 정상 종료하고, 다른 emoji token row는 유지한다.
  // 벌크 delete라 엔티티 로딩 없이 단일 statement로 처리한다. 영향 행 수를 반환한다.
  public long deleteReaction(Long missionLogId, Long memberId, String reactionType) {
    return queryFactory
        .delete(missionLogReaction)
        .where(
            missionLogReaction.missionLog.id.eq(missionLogId),
            missionLogReaction.member.id.eq(memberId),
            missionLogReaction.reactionType.eq(reactionType))
        .execute();
  }
}
