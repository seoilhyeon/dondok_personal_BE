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
  private String rejectMemo; // TODO: rejectCode Other시, memo 필수값

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "moderator_id", nullable = false)
  private Member moderator;

  @Column(name = "changed_at", nullable = false)
  private LocalDateTime changedAt;

  // 방장 수동 승인 이력 생성
  public static ModerationHistory createManualApprove(
      MissionLog missionLog,
      String beforeState,
      String afterState,
      Member moderator,
      LocalDateTime changedAt) {
    ModerationHistory history = new ModerationHistory();
    history.missionLog = missionLog;
    history.beforeState = beforeState;
    history.afterState = afterState;
    history.decisionType = ModerationDecisionType.MANUAL_APPROVE;
    history.rejectReasonCode = null;
    history.rejectMemo = null;
    history.moderator = moderator;
    history.changedAt = changedAt;
    return history;
  }
}
