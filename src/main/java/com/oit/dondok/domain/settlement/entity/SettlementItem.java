package com.oit.dondok.domain.settlement.entity;

import com.oit.dondok.domain.crew.entity.CrewParticipant;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "settlement_item",
    indexes = {
      @Index(name = "idx_settlement_item_member", columnList = "member_id"),
      @Index(name = "idx_settlement_item_participant", columnList = "crew_participant_id")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_settlement_item_settlement_participant",
          columnNames = {"settlement_id", "crew_participant_id"}),
      @UniqueConstraint(name = "uk_settlement_item_point_history", columnNames = "point_history_id")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementItem extends AuditableTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "settlement_id", nullable = false)
  private Settlement settlement;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "crew_participant_id", nullable = false)
  private CrewParticipant crewParticipant;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Enumerated(EnumType.STRING)
  @Column(name = "participant_status_snapshot", nullable = false, length = 20)
  private ParticipantStatusSnapshot participantStatusSnapshot;

  @Column(name = "deposit_amount", nullable = false)
  private Long depositAmount;

  @Column(name = "success_count_raw", nullable = false)
  private Integer successCountRaw;

  @Column(name = "recognized_success_count", nullable = false)
  private Integer recognizedSuccessCount;

  @Column(name = "recognized_dates_count", nullable = false)
  private Integer recognizedDatesCount;

  @Column(name = "excluded_success_count", nullable = false)
  private Integer excludedSuccessCount;

  @Column(name = "period_start_at", nullable = false)
  private LocalDateTime periodStartAt;

  @Column(name = "period_end_at", nullable = false)
  private LocalDateTime periodEndAt;

  @Column(name = "share_ratio", nullable = false, precision = 18, scale = 8)
  private BigDecimal shareRatio;

  @Column(name = "base_refund_amount", nullable = false)
  private Long baseRefundAmount;

  @Column(name = "remainder_bonus_amount", nullable = false)
  private Long remainderBonusAmount;

  @Column(name = "refund_amount", nullable = false)
  private Long refundAmount;

  @Column(name = "effective_moderation_snapshot", columnDefinition = "json")
  private String effectiveModerationSnapshot;

  @Column(name = "moderation_chain_ref", columnDefinition = "json")
  private String moderationChainRef;

  @Column(name = "calculation_reason", nullable = false, columnDefinition = "json")
  private String calculationReason;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "point_history_id")
  private PointHistory pointHistory;
}
