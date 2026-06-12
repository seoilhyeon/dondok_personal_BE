package com.oit.dondok.domain.crew.entity;

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

@Getter
@Entity
@Table(
    name = "crew_notice_reaction",
    indexes = {
      @Index(name = "idx_crew_notice_reaction_notice", columnList = "crew_notice_id"),
      @Index(name = "idx_crew_notice_reaction_member_created", columnList = "member_id, created_at")
    },
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_crew_notice_reaction_notice_member_type",
            columnNames = {"crew_notice_id", "member_id", "reaction_type"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CrewNoticeReaction extends AuditableTimeEntity {

  public static final int MAX_REACTION_TYPE_LENGTH = 20;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "crew_notice_id", nullable = false)
  private CrewNotice crewNotice;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Column(name = "reaction_type", nullable = false, length = 20)
  private String reactionType; // emoji token

  public static CrewNoticeReaction create(
      CrewNotice crewNotice, Member member, String reactionType) {
    CrewNoticeReaction reaction = new CrewNoticeReaction();
    reaction.crewNotice = crewNotice;
    reaction.member = member;
    reaction.reactionType = reactionType;
    return reaction;
  }
}
