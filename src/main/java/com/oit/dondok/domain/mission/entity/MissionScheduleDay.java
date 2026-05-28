package com.oit.dondok.domain.mission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "mission_schedule_day",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_mission_schedule_day_rule_day",
            columnNames = {"mission_rule_id", "day_of_week"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionScheduleDay {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "mission_rule_id", nullable = false)
  private MissionRule missionRule;

  @Column(name = "day_of_week", nullable = false, columnDefinition = "TINYINT")
  private Integer dayOfWeek;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;
}
