package com.oit.dondok.domain.crew.repository;

import static com.oit.dondok.domain.crew.entity.QCrew.crew;
import static com.oit.dondok.domain.crew.entity.QCrewParticipant.crewParticipant;
import static com.oit.dondok.domain.mission.entity.QMissionRule.missionRule;
import static com.oit.dondok.domain.mission.entity.QMissionScheduleDay.missionScheduleDay;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantRole;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.member.entity.QMember;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CrewQueryRepository {

  private final JPAQueryFactory queryFactory;

  public record CrewWithRule(Crew crew, MissionRule missionRule) {}

  public Optional<Crew> findCrewWithHost(Long crewId) {
    return Optional.ofNullable(
        queryFactory
            .select(crew)
            .from(crew)
            .join(crew.hostMember)
            .fetchJoin()
            .where(crew.id.eq(crewId))
            .fetchOne());
  }

  public List<CrewWithRule> findCrewsWithRule(
      CrewStatus status, String category, String keyword, Long cursorId, int limit) {
    BooleanBuilder predicate = new BooleanBuilder();
    predicate.and(crew.status.eq(status));
    if (category != null) {
      predicate.and(crew.category.eq(category));
    }
    if (keyword != null && !keyword.isBlank()) {
      predicate.and(crew.title.containsIgnoreCase(keyword));
    }
    if (cursorId != null) {
      predicate.and(crew.id.lt(cursorId));
    }

    List<Tuple> tuples =
        queryFactory
            .select(crew, missionRule)
            .from(crew)
            .join(missionRule)
            .on(missionRule.crew.eq(crew))
            .where(predicate)
            .orderBy(crew.id.desc())
            .limit(limit + 1L)
            .fetch();

    return tuples.stream().map(t -> new CrewWithRule(t.get(crew), t.get(missionRule))).toList();
  }

  public List<CrewParticipant> findMyCrewParticipants(
      UUID memberUuid,
      CrewParticipantRole role,
      CrewParticipantStatus myStatus,
      Long cursorId,
      int limit) {
    QMember hostMember = new QMember("hostMember");

    BooleanBuilder predicate = new BooleanBuilder();
    predicate.and(crewParticipant.member.uuid.eq(memberUuid));
    if (myStatus != null) {
      predicate.and(crewParticipant.status.eq(myStatus));
    } else {
      predicate.and(
          crewParticipant.status.in(CrewParticipantStatus.PENDING, CrewParticipantStatus.LOCKED));
    }
    if (cursorId != null) {
      predicate.and(crewParticipant.id.gt(cursorId));
    }
    if (role == CrewParticipantRole.HOST) {
      predicate.and(hostMember.uuid.eq(memberUuid));
    } else if (role == CrewParticipantRole.MEMBER) {
      predicate.and(hostMember.uuid.ne(memberUuid));
    }

    return queryFactory
        .selectFrom(crewParticipant)
        .join(crewParticipant.crew, crew)
        .fetchJoin()
        .join(crew.hostMember, hostMember)
        .fetchJoin()
        .where(predicate)
        .orderBy(crewParticipant.id.asc())
        .limit(limit + 1L)
        .fetch();
  }

  // LOCKED 상태로 보증금이 확정 잠긴, 현재 참여 중인 크루 참여 row 전체를 crew_id ASC로 조회한다.
  public List<CrewParticipant> findAMyLockedCrewParticipants(UUID memberUuid) {
    return queryFactory
        .selectFrom(crewParticipant)
        .join(crewParticipant.crew, crew)
        .fetchJoin()
        .where(
            crewParticipant
                .member
                .uuid
                .eq(memberUuid)
                .and(crewParticipant.status.eq(CrewParticipantStatus.LOCKED)))
        .orderBy(crew.id.asc())
        .fetch();
  }

  // 상태 무관 참여 이력 존재 여부.
  public boolean hasAnyCrewParticipant(UUID memberUuid) {
    Integer fetched =
        queryFactory
            .selectOne()
            .from(crewParticipant)
            .where(crewParticipant.member.uuid.eq(memberUuid))
            .fetchFirst();
    return fetched != null;
  }

  public Map<Long, List<String>> findScheduleDaysByRuleIds(List<Long> missionRuleIds) {
    if (missionRuleIds.isEmpty()) {
      return Map.of();
    }
    List<Tuple> rows =
        queryFactory
            .select(missionScheduleDay.missionRule.id, missionScheduleDay.dayOfWeek)
            .from(missionScheduleDay)
            .where(missionScheduleDay.missionRule.id.in(missionRuleIds))
            .orderBy(missionScheduleDay.dayOfWeek.asc())
            .fetch();

    return rows.stream()
        .collect(
            Collectors.groupingBy(
                t -> t.get(missionScheduleDay.missionRule.id),
                Collectors.mapping(
                    t -> DayOfWeek.of(t.get(missionScheduleDay.dayOfWeek)).name(),
                    Collectors.toList())));
  }
}
