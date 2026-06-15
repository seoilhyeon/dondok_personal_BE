package com.oit.dondok.domain.mission.repository;

import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionRule;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MissionRuleRepository extends JpaRepository<MissionRule, Long> {

  Optional<MissionRule> findByCrewId(Long crewId);

  @Query(
      """
      select mr
        from MissionRule mr
        join fetch mr.crew c
       where mr.dailySettlementType = :dailySettlementType
         and c.status = com.oit.dondok.domain.crew.entity.CrewStatus.ACTIVE
         and c.startAt < :missionDateEndExclusive
         and c.endAt >= :missionDateStartInclusive
         and (
           mr.frequencyType = com.oit.dondok.domain.mission.entity.MissionFrequencyType.DAILY
           or exists (
             select 1
               from MissionScheduleDay msd
              where msd.missionRule = mr
                and msd.dayOfWeek = :dayOfWeek
           )
         )
       order by c.id asc
      """)
  List<MissionRule> findActiveRulesForDailySettlement(
      @Param("dailySettlementType") DailySettlementType dailySettlementType,
      @Param("missionDateStartInclusive") LocalDateTime missionDateStartInclusive,
      @Param("missionDateEndExclusive") LocalDateTime missionDateEndExclusive,
      @Param("dayOfWeek") int dayOfWeek);
}
