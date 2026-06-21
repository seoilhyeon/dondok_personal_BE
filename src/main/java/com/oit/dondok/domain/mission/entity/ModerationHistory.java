package com.oit.dondok.domain.mission.entity;

import com.oit.dondok.domain.member.entity.Member;
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

@Getter
@Entity
@Table(
    name = "moderation_history",
    indexes =
        @Index(
            name = "idx_moderation_history_log_changed",
            columnList = "mission_log_id, changed_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModerationHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "mission_log_id", nullable = false)
  private MissionLog missionLog;

  @Column(name = "before_state", columnDefinition = "json")
  private String beforeState;

  @Column(name = "after_state", nullable = false, columnDefinition = "json")
  private String afterState;

  @Enumerated(EnumType.STRING)
  @Column(name = "decision_type", nullable = false, length = 20)
  private ModerationDecisionType decisionType;

  @Enumerated(EnumType.STRING)
  @Column(name = "reject_reason_code", length = 30)
  private RejectReasonCode rejectReasonCode;

  @Column(name = "reject_memo", length = 50)
  private String rejectMemo;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "moderator_id", nullable = false)
  private Member moderator;

  @Column(name = "changed_at", nullable = false)
  private LocalDateTime changedAt;

  // 방장 수동 승인 이력을 생성한다.
  public static ModerationHistory createManualApprove(
      MissionLog missionLog,
      String beforeState,
      String afterState,
      Member moderator,
      LocalDateTime changedAt) {
    return create(
        missionLog,
        beforeState,
        afterState,
        ModerationDecisionType.MANUAL_APPROVE,
        null,
        null,
        moderator,
        changedAt);
  }

  // 방장 수동 거절 이력을 생성한다.
  public static ModerationHistory createManualReject(
      MissionLog missionLog,
      String beforeState,
      String afterState,
      Member moderator,
      RejectReasonCode rejectReasonCode,
      String rejectMemo,
      LocalDateTime changedAt) {
    return create(
        missionLog,
        beforeState,
        afterState,
        ModerationDecisionType.MANUAL_REJECT,
        rejectReasonCode,
        rejectMemo,
        moderator,
        changedAt);
  }

  // 시스템 자동 승인 이력을 생성한다.
  public static ModerationHistory createAutoApprove(
      MissionLog missionLog,
      String beforeState,
      String afterState,
      Member moderator,
      LocalDateTime changedAt) {
    return create(
        missionLog,
        beforeState,
        afterState,
        ModerationDecisionType.AUTO_APPROVE,
        null,
        null,
        moderator,
        changedAt);
  }

  // 방장이 수동 결정을 검토 대기로 되돌린 이력을 생성한다.
  public static ModerationHistory createManualRevert(
      MissionLog missionLog,
      String beforeState,
      String afterState,
      Member moderator,
      LocalDateTime changedAt) {
    return create(
        missionLog,
        beforeState,
        afterState,
        ModerationDecisionType.MANUAL_REVERT,
        null,
        null,
        moderator,
        changedAt);
  }

  // 시스템 자동 반려 이력을 생성한다.
  public static ModerationHistory createAutoReject(
      MissionLog missionLog,
      String beforeState,
      String afterState,
      Member moderator,
      LocalDateTime changedAt) {
    return create(
        missionLog,
        beforeState,
        afterState,
        ModerationDecisionType.AUTO_REJECT,
        null,
        null,
        moderator,
        changedAt);
  }

  private static ModerationHistory create(
      MissionLog missionLog,
      String beforeState,
      String afterState,
      ModerationDecisionType decisionType,
      RejectReasonCode rejectReasonCode,
      String rejectMemo,
      Member moderator,
      LocalDateTime changedAt) {
    ModerationHistory history = new ModerationHistory();
    history.missionLog = missionLog;
    history.beforeState = beforeState;
    history.afterState = afterState;
    history.decisionType = decisionType;
    history.rejectReasonCode = rejectReasonCode;
    history.rejectMemo = rejectMemo;
    history.moderator = moderator;
    history.changedAt = changedAt;
    return history;
  }
}
