package com.oit.dondok.domain.mission.repository;

import static com.oit.dondok.domain.crew.entity.QCrew.crew;
import static com.oit.dondok.domain.crew.entity.QCrewParticipant.crewParticipant;
import static com.oit.dondok.domain.member.entity.QMember.member;
import static com.oit.dondok.domain.mission.entity.QMissionLog.missionLog;
import static com.oit.dondok.domain.mission.entity.QMissionLogReaction.missionLogReaction;

import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.mission.dto.response.AvailableCrewResponse;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FeedQueryRepository {
  private final JPAQueryFactory queryFactory;

  // 호출자가 LOCKED로 참여 중인 크루 (available_crews + 기본 조회 스코프)
  public List<AvailableCrewResponse> findParticipatingCrews(UUID memberUuid) {
    return queryFactory
        .select(Projections.constructor(AvailableCrewResponse.class, crew.id, crew.title))
        .from(crewParticipant)
        .join(crewParticipant.crew, crew)
        .where(
            crewParticipant.member.uuid.eq(memberUuid),
            crewParticipant.status.eq(CrewParticipantStatus.LOCKED))
        .orderBy(crew.id.asc())
        .fetch();
  }

  // feed_items: server_time desc, id desc 커서 페이지네이션. next_cursor 판단 위해 limit+1 fetch.
  // 날짜 필터·정렬·커서 모두 server_time(인증 수신 시각) 기준.
  public List<FeedItemRow> findFeedItems(
      Collection<Long> crewIds,
      LocalDateTime fromInclusive,
      LocalDateTime toExclusive,
      LocalDateTime cursorServerTime,
      Long cursorId,
      int limit) {
    if (crewIds.isEmpty()) {
      return List.of();
    }
    return queryFactory
        .select(
            Projections.constructor(
                FeedItemRow.class,
                missionLog.id,
                crew.id,
                crew.title,
                crewParticipant.id,
                member.uuid,
                member.nickname,
                member.profileImageS3Key,
                missionLog.imageS3Key,
                missionLog.caption,
                missionLog.serverTime,
                missionLog.exifTakenAt,
                missionLog.exifRisk,
                missionLog.duplicateHash,
                missionLog.certificationStatus,
                missionLog.rejectReasonCode,
                missionLog.decisionType))
        .from(missionLog)
        .join(missionLog.crewParticipant, crewParticipant)
        .join(crewParticipant.crew, crew)
        .join(crewParticipant.member, member)
        .where(
            crew.id.in(crewIds),
            goeOrNull(fromInclusive),
            ltOrNull(toExclusive),
            cursorPredicate(cursorServerTime, cursorId))
        .orderBy(missionLog.serverTime.desc(), missionLog.id.desc())
        .limit((long) limit + 1)
        .fetch();
  }

  // missionLogId 기준 단건 피드 아이템 조회. 존재하지 않으면 empty 반환.
  public Optional<FeedItemRow> findFeedItemById(Long missionLogId) {
    return Optional.ofNullable(
        queryFactory
            .select(
                Projections.constructor(
                    FeedItemRow.class,
                    missionLog.id,
                    crew.id,
                    crew.title,
                    crewParticipant.id,
                    member.uuid,
                    member.nickname,
                    member.profileImageS3Key,
                    missionLog.imageS3Key,
                    missionLog.caption,
                    missionLog.serverTime,
                    missionLog.exifTakenAt,
                    missionLog.exifRisk,
                    missionLog.duplicateHash,
                    missionLog.certificationStatus,
                    missionLog.rejectReasonCode,
                    missionLog.decisionType))
            .from(missionLog)
            .join(missionLog.crewParticipant, crewParticipant)
            .join(crewParticipant.crew, crew)
            .join(crewParticipant.member, member)
            .where(missionLog.id.eq(missionLogId))
            .fetchOne());
  }

  // 페이지 내 mission_log들의 모든 리액션 row를 한 번에 조회. 서비스에서 counts/my로 가공한다.
  public List<ReactionRow> findReactionRows(Collection<Long> missionLogIds) {
    if (missionLogIds.isEmpty()) {
      return List.of();
    }
    return queryFactory
        .select(
            Projections.constructor(
                ReactionRow.class,
                missionLogReaction.missionLog.id,
                missionLogReaction.reactionType,
                missionLogReaction.member.uuid,
                missionLogReaction.createdAt))
        .from(missionLogReaction)
        .where(missionLogReaction.missionLog.id.in(missionLogIds))
        .fetch();
  }

  private BooleanExpression goeOrNull(LocalDateTime fromInclusive) {
    return fromInclusive == null ? null : missionLog.serverTime.goe(fromInclusive);
  }

  private BooleanExpression ltOrNull(LocalDateTime toExclusive) {
    return toExclusive == null ? null : missionLog.serverTime.lt(toExclusive);
  }

  // (server_time, id) 복합 커서: server_time이 더 과거이거나, 같으면 id가 더 작은 row
  private BooleanExpression cursorPredicate(LocalDateTime cursorServerTime, Long cursorId) {
    if (cursorServerTime == null || cursorId == null) {
      return null;
    }
    return missionLog
        .serverTime
        .lt(cursorServerTime)
        .or(missionLog.serverTime.eq(cursorServerTime).and(missionLog.id.lt(cursorId)));
  }
}
