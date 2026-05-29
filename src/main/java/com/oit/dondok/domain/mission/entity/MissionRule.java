package com.oit.dondok.domain.mission.entity;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.global.entity.AuditableTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "mission_rule",
    uniqueConstraints = @UniqueConstraint(name = "uk_mission_rule_crew", columnNames = "crew_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionRule extends AuditableTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "crew_id", nullable = false, unique = true)
  private Crew crew;

  @Enumerated(EnumType.STRING)
  @Column(name = "frequency_type", nullable = false, length = 20)
  private MissionFrequencyType frequencyType;

  @Enumerated(EnumType.STRING)
  @Column(name = "daily_settlement_type", nullable = false, columnDefinition = "char(1)")
  private DailySettlementType dailySettlementType;
}
