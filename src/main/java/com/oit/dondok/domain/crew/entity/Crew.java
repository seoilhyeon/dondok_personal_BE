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
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

@Getter
@Entity
@Check(
    constraints =
        "min_participants >= 2 and min_participants <= max_participants and max_participants <= 15")
@Table(
    name = "crew",
    indexes = {
      @Index(name = "idx_crew_host_created", columnList = "host_member_id, created_at"),
      @Index(
          name = "idx_crew_status_recruitment_deadline",
          columnList = "status, recruitment_deadline"),
      @Index(name = "idx_crew_status_period", columnList = "status, start_at, end_at"),
      @Index(name = "idx_crew_status_activated", columnList = "status, activated_at")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Crew extends AuditableTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "host_member_id", nullable = false)
  private Member hostMember;

  @Column(name = "title", nullable = false, length = 100)
  private String title;

  @Column(name = "description", nullable = false, columnDefinition = "text")
  private String description;

  @Column(name = "image_s3_key", length = 255)
  private String imageS3Key;

  @Column(name = "category", nullable = false, length = 30)
  private String category;

  @Column(name = "host_agreement_snapshot", nullable = false, columnDefinition = "json")
  private String hostAgreementSnapshot;

  @Enumerated(EnumType.STRING)
  @Column(name = "host_agreement_version", nullable = false, length = 20)
  private HostPolicyVersion hostAgreementVersion;

  @Column(name = "host_agreed_at", nullable = false)
  private LocalDateTime hostAgreedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private CrewStatus status;

  @Column(name = "deposit_amount", nullable = false)
  private Long depositAmount;

  @Column(name = "min_participants", nullable = false)
  private Integer minParticipants;

  @Column(name = "max_participants", nullable = false)
  private Integer maxParticipants;

  @Column(name = "recruitment_deadline", nullable = false)
  private LocalDateTime recruitmentDeadline;

  @Column(name = "start_at", nullable = false)
  private LocalDateTime startAt;

  @Column(name = "activated_at")
  private LocalDateTime activatedAt;

  @Column(name = "cancelled_at")
  private LocalDateTime cancelledAt;

  @Column(name = "end_at", nullable = false)
  private LocalDateTime endAt;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  public void activate(LocalDateTime now) {
    if (this.status != CrewStatus.RECRUITING) {
      // TODO: 서비스 레이어 재사용 시 CustomException + CrewErrorCode로 교체 필요
      throw new IllegalStateException("activate는 RECRUITING 상태에서만 가능합니다.");
    }
    this.status = CrewStatus.ACTIVE;
    this.activatedAt = now;
  }

  public void cancel(LocalDateTime now) {
    if (this.status != CrewStatus.RECRUITING) {
      // TODO: 서비스 레이어 재사용 시 CustomException + CrewErrorCode로 교체 필요
      throw new IllegalStateException("cancel은 RECRUITING 상태에서만 가능합니다.");
    }
    this.status = CrewStatus.CANCELLED;
    this.cancelledAt = now;
  }

  public static Crew create(
      Member hostMember,
      String title,
      String description,
      String imageS3Key,
      String category,
      String hostAgreementSnapshot,
      HostPolicyVersion hostAgreementVersion,
      LocalDateTime hostAgreedAt,
      Long depositAmount,
      Integer minParticipants,
      Integer maxParticipants,
      LocalDateTime recruitmentDeadline,
      LocalDateTime startAt,
      LocalDateTime endAt) {
    Crew crew = new Crew();
    crew.hostMember = hostMember;
    crew.title = title;
    crew.description = description;
    crew.imageS3Key = imageS3Key;
    crew.category = category;
    crew.hostAgreementSnapshot = hostAgreementSnapshot;
    crew.hostAgreementVersion = hostAgreementVersion;
    crew.hostAgreedAt = hostAgreedAt;
    crew.status = CrewStatus.RECRUITING;
    crew.depositAmount = depositAmount;
    crew.minParticipants = minParticipants;
    crew.maxParticipants = maxParticipants;
    crew.recruitmentDeadline = recruitmentDeadline;
    crew.startAt = startAt;
    crew.endAt = endAt;
    return crew;
  }
}
