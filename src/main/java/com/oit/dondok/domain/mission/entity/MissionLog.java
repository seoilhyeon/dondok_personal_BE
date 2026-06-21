package com.oit.dondok.domain.mission.entity;

import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.exception.MissionErrorCode;
import com.oit.dondok.global.entity.AuditableTimeEntity;
import com.oit.dondok.global.exception.CustomException;
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
// Keep automatic decision signal rules aligned with ExifRisk and the Flyway CHECK constraint.
@Check(constraints = "char_length(caption) between 5 and 100")
@Check(
    constraints =
        "("
            + "   (certification_status = 'FAILED'"
            + "     and ("
            + "       (decision_type = 'MANUAL_REJECT'"
            + "         and reject_reason_code is not null"
            + "         and (reject_reason_code <> 'OTHER'"
            + "           or (reject_memo is not null and trim(reject_memo) <> '')))"
            + "       or"
            + "       (decision_type = 'AUTO_REJECT'"
            + "         and (duplicate_hash = true or exif_risk not in ('NORMAL', 'MISSING'))"
            + "         and reject_reason_code is null"
            + "         and reject_memo is null)"
            + "     )"
            + "   )"
            + "   or"
            + "   (certification_status <> 'FAILED'"
            + "     and (decision_type is null"
            + "       or decision_type <> 'AUTO_APPROVE'"
            + "       or (duplicate_hash = false and exif_risk in ('NORMAL', 'MISSING')))"
            + "     and reject_reason_code is null"
            + "     and reject_memo is null)"
            + " )")
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
  private String caption;

  @Column(name = "image_hash", columnDefinition = "char(64)")
  private String imageHash;

  @Column(name = "server_time", nullable = false)
  private LocalDateTime serverTime;

  @Column(name = "exif_taken_at")
  private LocalDateTime exifTakenAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "exif_risk", nullable = false, length = 20)
  private ExifRisk exifRisk;

  @Column(name = "duplicate_hash", nullable = false)
  private boolean duplicateHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "certification_status", nullable = false, length = 20)
  private CertificationStatus certificationStatus;

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

  // 제출 직후 인증 로그는 항상 PENDING_REVIEW 상태로 생성한다.
  // image_url은 저장하지 않고 조회 시 ImageDeliveryPort가 임시 URL을 생성한다.
  // exifRisk와 duplicateHash는 제출 시점에 계산한 방장 검토 보조 신호다.
  public static MissionLog createPendingReview(
      CrewParticipant crewParticipant,
      String imageS3Key,
      String caption,
      String imageHash,
      LocalDateTime exifTakenAt,
      ExifRisk exifRisk,
      boolean duplicateHash,
      LocalDateTime serverTime) {
    MissionLog missionLog = new MissionLog();
    missionLog.crewParticipant = crewParticipant;
    missionLog.imageS3Key = imageS3Key;
    missionLog.caption = caption;
    missionLog.imageHash = imageHash;
    missionLog.exifTakenAt = exifTakenAt;
    missionLog.exifRisk = exifRisk;
    missionLog.duplicateHash = duplicateHash;
    missionLog.serverTime = serverTime;
    missionLog.certificationStatus = CertificationStatus.PENDING_REVIEW;
    return missionLog;
  }

  // 방장 수동 승인으로 인증 상태를 SUCCESS로 전환한다.
  public void approveManually(Member moderator, LocalDateTime decidedAt) {
    if (!isHostReviewable()) {
      throw new CustomException(MissionErrorCode.MISSION_LOG_NOT_REVIEWABLE);
    }

    this.certificationStatus = CertificationStatus.SUCCESS;
    this.moderator = moderator;
    this.moderatorDecidedAt = decidedAt;
    this.decisionType = ModerationDecisionType.MANUAL_APPROVE;
    this.rejectReasonCode = null;
    this.rejectMemo = null;
  }

  // 시스템 자동 승인으로 인증 상태를 SUCCESS로 전환한다.
  public void approveAutomatically(Member moderator, LocalDateTime decidedAt) {
    if (certificationStatus != CertificationStatus.PENDING_REVIEW) {
      throw new CustomException(MissionErrorCode.MISSION_LOG_NOT_REVIEWABLE);
    }
    if (duplicateHash || !exifRisk.isAutoApprovable()) {
      throw new CustomException(MissionErrorCode.INVALID_AUTO_APPROVAL_SIGNAL);
    }

    this.certificationStatus = CertificationStatus.SUCCESS;
    this.moderator = moderator;
    this.moderatorDecidedAt = decidedAt;
    this.decisionType = ModerationDecisionType.AUTO_APPROVE;
    this.rejectReasonCode = null;
    this.rejectMemo = null;
  }

  // 방장 수동 거절로 인증 상태를 FAILED로 전환한다.
  public void rejectManually(
      Member moderator,
      RejectReasonCode rejectReasonCode,
      String rejectMemo,
      LocalDateTime decidedAt) {
    if (!isHostReviewable()) {
      throw new CustomException(MissionErrorCode.MISSION_LOG_NOT_REVIEWABLE);
    }

    this.certificationStatus = CertificationStatus.FAILED;
    this.moderator = moderator;
    this.moderatorDecidedAt = decidedAt;
    this.decisionType = ModerationDecisionType.MANUAL_REJECT;
    this.rejectReasonCode = rejectReasonCode;
    this.rejectMemo = rejectMemo;
  }

  // 시스템 자동 반려로 인증 상태를 FAILED로 전환한다.
  public void rejectAutomatically(Member moderator, LocalDateTime decidedAt) {
    if (certificationStatus != CertificationStatus.PENDING_REVIEW) {
      throw new CustomException(MissionErrorCode.MISSION_LOG_NOT_REVIEWABLE);
    }
    if (!duplicateHash && exifRisk.isAutoApprovable()) {
      throw new CustomException(MissionErrorCode.INVALID_AUTO_REJECTION_SIGNAL);
    }

    this.certificationStatus = CertificationStatus.FAILED;
    this.moderator = moderator;
    this.moderatorDecidedAt = decidedAt;
    this.decisionType = ModerationDecisionType.AUTO_REJECT;
    this.rejectReasonCode = null;
    this.rejectMemo = null;
  }

  // 방장이 수동으로 결정한 인증(MANUAL_APPROVE/MANUAL_REJECT)만 검토 대기로 되돌릴 수 있다.
  public void revertToPendingReview() {
    if (!isRevertible()) {
      throw new CustomException(MissionErrorCode.MISSION_LOG_NOT_REVERTIBLE);
    }
    this.certificationStatus = CertificationStatus.PENDING_REVIEW;
    this.decisionType = null;
    this.moderator = null;
    this.moderatorDecidedAt = null;
    this.rejectReasonCode = null;
    this.rejectMemo = null;
  }

  public boolean isRevertible() {
    return (certificationStatus == CertificationStatus.SUCCESS
            && decisionType == ModerationDecisionType.MANUAL_APPROVE)
        || (certificationStatus == CertificationStatus.FAILED
            && decisionType == ModerationDecisionType.MANUAL_REJECT);
  }

  public boolean isPendingReview() {
    return certificationStatus == CertificationStatus.PENDING_REVIEW;
  }

  public boolean isHostReviewable() {
    return certificationStatus == CertificationStatus.PENDING_REVIEW
        || (certificationStatus == CertificationStatus.SUCCESS
            && decisionType == ModerationDecisionType.AUTO_APPROVE)
        || (certificationStatus == CertificationStatus.FAILED
            && decisionType == ModerationDecisionType.AUTO_REJECT);
  }
}
