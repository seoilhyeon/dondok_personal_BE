package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.repository.MissionScheduleDayRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FinalSettlementMissionDateResolver {

  private final MissionScheduleDayRepository missionScheduleDayRepository;

  public List<LocalDate> resolveMissionDates(Crew crew, MissionRule missionRule) {
    LocalDate startDate = crew.getStartAt().toLocalDate();
    LocalDate endDate = crew.getEndAt().toLocalDate();
    if (endDate.isBefore(startDate)) {
      return List.of();
    }

    if (missionRule.getFrequencyType() == MissionFrequencyType.DAILY) {
      return startDate.datesUntil(endDate.plusDays(1)).toList();
    }

    Set<Integer> missionDays =
        missionScheduleDayRepository
            .findByMissionRuleIdOrderByDayOfWeekAsc(missionRule.getId())
            .stream()
            .map(day -> day.getDayOfWeek())
            .collect(Collectors.toSet());
    if (missionDays.isEmpty()) {
      return List.of();
    }

    return Stream.iterate(startDate, date -> !date.isAfter(endDate), date -> date.plusDays(1))
        .filter(date -> missionDays.contains(date.getDayOfWeek().getValue()))
        .toList();
  }
}
