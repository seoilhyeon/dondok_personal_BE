package com.oit.dondok.domain.crew.entity;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.global.entity.AuditableTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "crew_notice",
    indexes = {
      @Index(
          name = "idx_crew_notice_crew_status_created",
          columnList = "crew_id, status, created_at"),
      @Index(name = "idx_crew_notice_author_created", columnList = "author_member_id, created_at")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CrewNotice extends AuditableTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "crew_id", nullable = false)
  private Crew crew;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "author_member_id", nullable = false)
  private Member authorMember;

  @Column(name = "title", length = 100)
  private String title;

  @Column(name = "content", nullable = false, columnDefinition = "text")
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private CrewNoticeStatus status;
}
