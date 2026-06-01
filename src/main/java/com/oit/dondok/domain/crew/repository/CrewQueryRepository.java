package com.oit.dondok.domain.crew.repository;

import static com.oit.dondok.domain.crew.entity.QCrew.crew;
import static com.oit.dondok.domain.mission.entity.QMissionRule.missionRule;
import static com.oit.dondok.domain.mission.entity.QMissionScheduleDay.missionScheduleDay;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewCategory;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CrewQueryRepository {

  private final JPAQueryFactory queryFactory;

  public record CrewWithRule(Crew crew, MissionRule missionRule) {}

  public List<CrewWithRule> findCrewsWithRule(
      CrewStatus status, CrewCategory category, String keyword, Long cursorId, int limit) {
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
