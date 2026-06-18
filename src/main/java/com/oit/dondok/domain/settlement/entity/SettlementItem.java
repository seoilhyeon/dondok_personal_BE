package com.oit.dondok.domain.settlement.entity;

import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.point.entity.PointHistory;
import com.oit.dondok.domain.settlement.entity.converter.SettlementCalculationReasonConverter;
import com.oit.dondok.global.entity.AuditableTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.util.Objects;
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

  // 정산 결과 화면 표시용 참여자 닉네임 스냅샷 (정산 시점 값)
  @Column(name = "nickname", length = 100)
  private String nickname;

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

  @Column(name = "share_ratio", nullable = false, precision = 10, scale = 6)
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

  @Convert(converter = SettlementCalculationReasonConverter.class)
  @Column(name = "calculation_reason", nullable = false, columnDefinition = "json")
  private SettlementCalculationReason calculationReason;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "point_history_id")
  private PointHistory pointHistory;

  // 신규 SettlementItem 생성 전용. nickname은 정산 시점 값을 고정하는 불변 스냅샷이다.
  // 기존 item 재사용/수정 흐름에서는 호출 금지 — 재호출 시 스냅샷이 현재 닉네임으로 덮어써진다.
  public static SettlementItem create(
      Settlement settlement,
      CrewParticipant crewParticipant,
      Long depositAmount,
      Integer successCountRaw,
      Integer recognizedSuccessCount,
      Integer recognizedDatesCount,
      Integer excludedSuccessCount,
      LocalDateTime periodStartAt,
      LocalDateTime periodEndAt,
      BigDecimal shareRatio,
      Long baseRefundAmount,
      Long remainderBonusAmount,
      Long refundAmount,
      SettlementCalculationReason calculationReason,
      String effectiveModerationSnapshot,
      String moderationChainRef) {
    SettlementItem item = new SettlementItem();
    item.settlement = Objects.requireNonNull(settlement, "정산은 필수입니다.");
    item.crewParticipant = Objects.requireNonNull(crewParticipant, "크루 참여자는 필수입니다.");
    item.member = Objects.requireNonNull(crewParticipant.getMember(), "회원은 필수입니다.");
    item.nickname = item.member.getNickname();
    item.participantStatusSnapshot = ParticipantStatusSnapshot.LOCKED;
    item.depositAmount = Objects.requireNonNull(depositAmount, "예치금은 필수입니다.");
    item.successCountRaw = Objects.requireNonNull(successCountRaw, "성공 횟수(원본)는 필수입니다.");
    item.recognizedSuccessCount =
        Objects.requireNonNull(recognizedSuccessCount, "인정된 성공 횟수는 필수입니다.");
    item.recognizedDatesCount = Objects.requireNonNull(recognizedDatesCount, "인정된 날짜 수는 필수입니다.");
    item.excludedSuccessCount = Objects.requireNonNull(excludedSuccessCount, "제외된 성공 횟수는 필수입니다.");
    item.periodStartAt = Objects.requireNonNull(periodStartAt, "시작일자는 필수입니다.");
    item.periodEndAt = Objects.requireNonNull(periodEndAt, "종료일자는 필수입니다.");
    item.shareRatio = Objects.requireNonNull(shareRatio, "지분 비율은 필수입니다.");
    item.baseRefundAmount = Objects.requireNonNull(baseRefundAmount, "기본 환급 금액은 필수입니다.");
    item.remainderBonusAmount = Objects.requireNonNull(remainderBonusAmount, "나머지 보너스 금액은 필수입니다.");
    item.refundAmount = Objects.requireNonNull(refundAmount, "환급 금액은 필수입니다.");
    item.calculationReason = Objects.requireNonNull(calculationReason, "정산 계산 사유는 필수입니다.");
    item.effectiveModerationSnapshot = effectiveModerationSnapshot;
    item.moderationChainRef = moderationChainRef;
    return item;
  }

  public boolean matchesCalculation(
      Long depositAmount,
      Integer successCountRaw,
      Integer recognizedSuccessCount,
      Integer recognizedDatesCount,
      Integer excludedSuccessCount,
      LocalDateTime periodStartAt,
      LocalDateTime periodEndAt,
      BigDecimal shareRatio,
      Long baseRefundAmount,
      Long remainderBonusAmount,
      Long refundAmount) {
    return Objects.equals(this.depositAmount, depositAmount)
        && Objects.equals(this.successCountRaw, successCountRaw)
        && Objects.equals(this.recognizedSuccessCount, recognizedSuccessCount)
        && Objects.equals(this.recognizedDatesCount, recognizedDatesCount)
        && Objects.equals(this.excludedSuccessCount, excludedSuccessCount)
        && Objects.equals(periodStartAt, this.periodStartAt)
        && Objects.equals(periodEndAt, this.periodEndAt)
        && (this.shareRatio == null && shareRatio == null
            || this.shareRatio != null
                && shareRatio != null
                && this.shareRatio.compareTo(shareRatio) == 0)
        && Objects.equals(this.baseRefundAmount, baseRefundAmount)
        && Objects.equals(this.remainderBonusAmount, remainderBonusAmount)
        && Objects.equals(this.refundAmount, refundAmount);
  }

  public void linkPointHistory(PointHistory pointHistory) {
    Objects.requireNonNull(pointHistory, "포인트 이력은 필수입니다.");
    if (this.pointHistory != null) {
      throw new IllegalStateException("포인트 이력은 이미 연결되어 있습니다.");
    }
    this.pointHistory = pointHistory;
  }
}
