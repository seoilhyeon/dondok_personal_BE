package com.oit.dondok.domain.crew.entity;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.point.entity.PointHistory;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "crew_participant",
    indexes = {
      @Index(name = "idx_crew_participant_crew_status", columnList = "crew_id, status"),
      @Index(name = "idx_crew_participant_member_status", columnList = "member_id, status")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_crew_participant_crew_member",
          columnNames = {"crew_id", "member_id"}),
      @UniqueConstraint(
          name = "uk_crew_participant_released_point_history",
          columnNames = "released_point_history_id")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CrewParticipant extends AuditableTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "crew_id", nullable = false)
  private Crew crew;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private CrewParticipantStatus status;

  @Column(name = "deposit_amount", nullable = false)
  private Long depositAmount;

  @Column(name = "pending_at", nullable = false)
  private LocalDateTime pendingAt;

  @Column(name = "locked_at")
  private LocalDateTime lockedAt;

  @Column(name = "rejected_at")
  private LocalDateTime rejectedAt;

  @Column(name = "cancelled_at")
  private LocalDateTime cancelledAt;

  @Column(name = "expired_at")
  private LocalDateTime expiredAt;

  // 현재 reserve lifecycle을 종료시킨 release 근거 row를 가리킨다. 과거 release 기록 자체는 point_history에 계속 남아있는다.
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "released_point_history_id")
  private PointHistory releasedPointHistory;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  public static CrewParticipant create(
      Crew crew, Member member, Long depositAmount, LocalDateTime lockedAt) {
    CrewParticipant participant = new CrewParticipant();
    participant.crew = crew;
    participant.member = member;
    participant.status = CrewParticipantStatus.LOCKED;
    participant.depositAmount = depositAmount;
    participant.pendingAt = lockedAt;
    participant.lockedAt = lockedAt;
    return participant;
  }
}
