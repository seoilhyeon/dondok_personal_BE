package com.oit.dondok.domain.mission.entity;

import com.oit.dondok.domain.crew.entity.CrewParticipant;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

@Getter
@Check(
    constraints =
        "char_length(caption) between 5 and 100"
            + " and ((certification_status = 'FAILED' and failure_reason is not null)"
            + " or (certification_status <> 'FAILED' and failure_reason is null))")
@Entity
@Table(
    name = "mission_log",
    indexes = {
      @Index(
          name = "idx_mission_log_participant_time",
          columnList = "crew_participant_id, server_time"),
      @Index(
          name = "idx_mission_log_participant_status_time",
          columnList = "crew_participant_id, certification_status, server_time")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionLog extends AuditableTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "crew_participant_id", nullable = false)
  private CrewParticipant crewParticipant;

  @Column(name = "image_url", length = 500)
  private String imageUrl;

  @Column(name = "image_s3_key", nullable = false)
  private String imageS3Key;

  @Column(name = "caption", nullable = false, length = 100)
  private String caption; // 사진과 함께 제출하는 필수 인증 텍스트. 5~100자

  @Column(name = "image_hash", columnDefinition = "char(64)")
  private String imageHash;

  @Column(name = "server_time", nullable = false)
  private LocalDateTime serverTime;

  @Column(name = "exif_taken_at")
  private LocalDateTime exifTakenAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "certification_status", nullable = false, length = 20)
  private CertificationStatus certificationStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "failure_reason", length = 50)
  private MissionFailureReason failureReason;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "moderator_id")
  private Member moderator;

  @Column(name = "moderator_decided_at")
  private LocalDateTime moderatorDecidedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "decision_type", length = 20)
  private ModerationDecisionType decisionType;

  @Enumerated(EnumType.STRING)
  @Column(name = "reject_reason_code", length = 30)
  private RejectReasonCode rejectReasonCode;

  @Column(name = "reject_memo", length = 50)
  private String rejectMemo;
}
