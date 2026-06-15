package com.oit.dondok.domain.settlement.entity;

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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "daily_settlement_participant_snapshot",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_daily_settlement_participant_snapshot",
            columnNames = {"daily_settlement_snapshot_id", "crew_participant_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailySettlementParticipantSnapshot extends AuditableTimeEntity {

  private static final int SHARE_RATIO_SCALE = 6;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "daily_settlement_snapshot_id", nullable = false)
  private DailySettlementSnapshot dailySettlementSnapshot;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "crew_participant_id", nullable = false)
  private CrewParticipant crewParticipant;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Enumerated(EnumType.STRING)
  @Column(name = "participant_status_snapshot", nullable = false, length = 20)
  private ParticipantStatusSnapshot participantStatusSnapshot;

  // 당일 대시보드용 임시 예상값이다. 최종 환급 정산의 지급 기준이 아니다.
  @Column(name = "success_count", nullable = false)
  private Integer successCount;

  // 당일 대시보드용 임시 예상 지분율이다. 최종 환급 정산의 지급 기준이 아니다.
  @Column(name = "share_ratio", nullable = false, precision = 10, scale = 6)
  private BigDecimal shareRatio;

  // 당일 대시보드용 임시 예상 환급액이다. 최종 환급 정산의 지급 기준이 아니다.
  @Column(name = "expected_refund_amount", nullable = false)
  private Long expectedRefundAmount;

  public static DailySettlementParticipantSnapshot create(
      DailySettlementSnapshot dailySettlementSnapshot,
      CrewParticipant crewParticipant,
      int successCount,
      BigDecimal shareRatio,
      long expectedRefundAmount) {
    validateNonNegative(successCount, expectedRefundAmount);
    DailySettlementParticipantSnapshot snapshot = new DailySettlementParticipantSnapshot();
    snapshot.dailySettlementSnapshot =
        Objects.requireNonNull(dailySettlementSnapshot, "일일 정산 스냅샷은 필수입니다.");
    snapshot.crewParticipant = Objects.requireNonNull(crewParticipant, "크루 참여자는 필수입니다.");
    snapshot.member = Objects.requireNonNull(crewParticipant.getMember(), "참여자 회원은 필수입니다.");
    snapshot.participantStatusSnapshot = ParticipantStatusSnapshot.LOCKED;
    snapshot.successCount = successCount;
    snapshot.shareRatio =
        Objects.requireNonNull(shareRatio, "지분율은 필수입니다.")
            .setScale(SHARE_RATIO_SCALE, RoundingMode.FLOOR);
    snapshot.expectedRefundAmount = expectedRefundAmount;
    return snapshot;
  }

  private static void validateNonNegative(int successCount, long expectedRefundAmount) {
    if (successCount < 0 || expectedRefundAmount < 0) {
      throw new IllegalArgumentException("일일 정산 참여자 스냅샷 값은 음수일 수 없습니다.");
    }
  }
}
