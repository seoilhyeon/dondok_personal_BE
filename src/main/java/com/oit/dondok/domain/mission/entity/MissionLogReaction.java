package com.oit.dondok.domain.mission.entity;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.global.entity.AuditableTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

@Getter
@Check(constraints = "char_length(trim(reaction_type)) between 1 and 20")
@Entity
@Table(
    name = "mission_log_reaction",
    indexes = {
      @Index(name = "idx_mission_log_reaction_log", columnList = "mission_log_id"),
      @Index(name = "idx_mission_log_reaction_member_created", columnList = "member_id, created_at")
    },
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_mission_log_reaction_log_member_type",
            columnNames = {"mission_log_id", "member_id", "reaction_type"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionLogReaction extends AuditableTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "mission_log_id", nullable = false)
  private MissionLog missionLog;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Column(name = "reaction_type", nullable = false, length = 20)
  private String reactionType; // emoji token
}
