package com.oit.dondok.domain.settlement.entity;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "daily_settlement_snapshot",
    indexes = @Index(name = "idx_daily_settlement_snapshot_status", columnList = "status"),
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_daily_settlement_snapshot_crew_date_type",
            columnNames = {"crew_id", "mission_date", "daily_settlement_type"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailySettlementSnapshot extends AuditableTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "crew_id", nullable = false)
  private Crew crew;

  @Column(name = "mission_date", nullable = false)
  private LocalDate missionDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "daily_settlement_type", nullable = false, columnDefinition = "char(1)")
  private DailySettlementType dailySettlementType;

  @Enumerated(EnumType.STRING)
  @Column(name = "frequency_type_snapshot", nullable = false, length = 20)
  private MissionFrequencyType frequencyTypeSnapshot;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private DailySettlementStatus status;

  @Column(name = "batch_run_key", nullable = false, length = 100)
  private String batchRunKey;

  @Column(name = "frozen_at", nullable = false)
  private LocalDateTime frozenAt;

  @Column(name = "total_participants", nullable = false)
  private Integer totalParticipants;

  @Column(name = "total_recognized_success_count", nullable = false)
  private Integer totalRecognizedSuccessCount;

  @Column(name = "total_locked_amount", nullable = false)
  private Long totalLockedAmount;

  @Column(name = "failure_message", length = 500)
  private String failureMessage;

  public static DailySettlementSnapshot succeeded(
      Crew crew,
      LocalDate missionDate,
      DailySettlementType dailySettlementType,
      MissionFrequencyType frequencyTypeSnapshot,
      String batchRunKey,
      LocalDateTime frozenAt,
      int totalParticipants,
      int totalRecognizedSuccessCount,
      long totalLockedAmount) {
    validateNonNegative(totalParticipants, totalRecognizedSuccessCount, totalLockedAmount);
    DailySettlementSnapshot snapshot = new DailySettlementSnapshot();
    snapshot.crew = Objects.requireNonNull(crew, "크루는 필수입니다.");
    snapshot.missionDate = Objects.requireNonNull(missionDate, "미션 날짜는 필수입니다.");
    snapshot.dailySettlementType = Objects.requireNonNull(dailySettlementType, "일일 정산 타입은 필수입니다.");
    snapshot.frequencyTypeSnapshot =
        Objects.requireNonNull(frequencyTypeSnapshot, "미션 빈도 타입은 필수입니다.");
    snapshot.status = DailySettlementStatus.SUCCEEDED;
    snapshot.batchRunKey = Objects.requireNonNull(batchRunKey, "배치 실행 키는 필수입니다.");
    snapshot.frozenAt = Objects.requireNonNull(frozenAt, "스냅샷 고정 시각은 필수입니다.");
    snapshot.totalParticipants = totalParticipants;
    snapshot.totalRecognizedSuccessCount = totalRecognizedSuccessCount;
    snapshot.totalLockedAmount = totalLockedAmount;
    return snapshot;
  }

  private static void validateNonNegative(
      int totalParticipants, int totalRecognizedSuccessCount, long totalLockedAmount) {
    if (totalParticipants < 0 || totalRecognizedSuccessCount < 0 || totalLockedAmount < 0) {
      throw new IllegalArgumentException("일일 정산 스냅샷 집계 값은 음수일 수 없습니다.");
    }
  }
}
