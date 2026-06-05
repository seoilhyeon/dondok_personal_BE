package com.oit.dondok.domain.mission.repository;

import com.oit.dondok.domain.mission.entity.MissionScheduleDay;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionScheduleDayRepository extends JpaRepository<MissionScheduleDay, Long> {

  // SPECIFIC_DAYS 크루의 미션 가능 요일(1=월 ~7=일, ISO-8601) 포함 여부
  boolean existsByMissionRuleIdAndDayOfWeek(Long missionRuleId, Integer dayOfWeek);
}
