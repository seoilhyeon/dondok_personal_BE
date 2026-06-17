package com.oit.dondok.domain.settlement.entity;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.global.entity.AuditableTimeEntity;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
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
    indexes = {
      @Index(name = "idx_daily_settlement_snapshot_status", columnList = "status"),
      @Index(name = "idx_daily_settlement_snapshot_phase", columnList = "phase")
    },
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_daily_settlement_snapshot_crew_date_type_phase",
            columnNames = {"crew_id", "mission_date", "daily_settlement_type", "phase"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailySettlementSnapshot extends AuditableTimeEntity {

  public static final int MAX_RETRY_COUNT = 3;

  private static final int FAILURE_MESSAGE_MAX_LENGTH = 500;

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
  @Column(name = "phase", nullable = false, length = 20)
  private DailySettlementPhase phase;

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

  @Column(name = "retry_count", nullable = false)
  private Integer retryCount;

  // 당일 대시보드용 임시 예상 스냅샷이다. 최종 환급 정산의 지급 기준이 아니다.
  public static DailySettlementSnapshot provisional(
      Crew crew,
      LocalDate missionDate,
      DailySettlementType dailySettlementType,
      MissionFrequencyType frequencyTypeSnapshot,
      String batchRunKey,
      LocalDateTime frozenAt,
      int totalParticipants,
      int totalRecognizedSuccessCount,
      long totalLockedAmount) {
    return createSucceeded(
        DailySettlementPhase.PROVISIONAL,
        crew,
        missionDate,
        dailySettlementType,
        frequencyTypeSnapshot,
        batchRunKey,
        frozenAt,
        totalParticipants,
        totalRecognizedSuccessCount,
        totalLockedAmount);
  }

  // 방장 수정 유예가 끝난 AUTO_APPROVE까지 반영한 확정 스냅샷이다.
  public static DailySettlementSnapshot finalized(
      Crew crew,
      LocalDate missionDate,
      DailySettlementType dailySettlementType,
      MissionFrequencyType frequencyTypeSnapshot,
      String batchRunKey,
      LocalDateTime frozenAt,
      int totalParticipants,
      int totalRecognizedSuccessCount,
      long totalLockedAmount) {
    return createSucceeded(
        DailySettlementPhase.FINALIZED,
        crew,
        missionDate,
        dailySettlementType,
        frequencyTypeSnapshot,
        batchRunKey,
        frozenAt,
        totalParticipants,
        totalRecognizedSuccessCount,
        totalLockedAmount);
  }

  public static DailySettlementSnapshot provisionalFailed(
      Crew crew,
      LocalDate missionDate,
      DailySettlementType dailySettlementType,
      MissionFrequencyType frequencyTypeSnapshot,
      String batchRunKey,
      LocalDateTime frozenAt,
      String failureMessage) {
    return createFailed(
        DailySettlementPhase.PROVISIONAL,
        crew,
        missionDate,
        dailySettlementType,
        frequencyTypeSnapshot,
        batchRunKey,
        frozenAt,
        failureMessage);
  }

  public static DailySettlementSnapshot finalizedFailed(
      Crew crew,
      LocalDate missionDate,
      DailySettlementType dailySettlementType,
      MissionFrequencyType frequencyTypeSnapshot,
      String batchRunKey,
      LocalDateTime frozenAt,
      String failureMessage) {
    return createFailed(
        DailySettlementPhase.FINALIZED,
        crew,
        missionDate,
        dailySettlementType,
        frequencyTypeSnapshot,
        batchRunKey,
        frozenAt,
        failureMessage);
  }

  public void markSucceeded(
      String batchRunKey,
      LocalDateTime frozenAt,
      int totalParticipants,
      int totalRecognizedSuccessCount,
      long totalLockedAmount) {
    validateNonNegative(totalParticipants, totalRecognizedSuccessCount, totalLockedAmount);
    this.batchRunKey = Objects.requireNonNull(batchRunKey, "배치 실행 키는 필수입니다.");
    this.frozenAt = Objects.requireNonNull(frozenAt, "스냅샷 고정 시각은 필수입니다.");
    this.status = DailySettlementStatus.SUCCEEDED;
    this.retryCount = 0;
    this.totalParticipants = totalParticipants;
    this.totalRecognizedSuccessCount = totalRecognizedSuccessCount;
    this.totalLockedAmount = totalLockedAmount;
    this.failureMessage = null;
  }

  public void markFailed(String batchRunKey, LocalDateTime frozenAt, String failureMessage) {
    this.batchRunKey = Objects.requireNonNull(batchRunKey, "배치 실행 키는 필수입니다.");
    this.frozenAt = Objects.requireNonNull(frozenAt, "스냅샷 고정 시각은 필수입니다.");
    this.status = DailySettlementStatus.FAILED;
    this.retryCount = Math.min(this.retryCount + 1, MAX_RETRY_COUNT);
    this.totalParticipants = 0;
    this.totalRecognizedSuccessCount = 0;
    this.totalLockedAmount = 0L;
    this.failureMessage = truncateFailureMessage(failureMessage);
  }

  public boolean canRetry() {
    return status == DailySettlementStatus.FAILED && retryCount < MAX_RETRY_COUNT;
  }

  private static DailySettlementSnapshot createFailed(
      DailySettlementPhase phase,
      Crew crew,
      LocalDate missionDate,
      DailySettlementType dailySettlementType,
      MissionFrequencyType frequencyTypeSnapshot,
      String batchRunKey,
      LocalDateTime frozenAt,
      String failureMessage) {
    DailySettlementSnapshot snapshot = new DailySettlementSnapshot();
    snapshot.assignIdentity(
        phase,
        crew,
        missionDate,
        dailySettlementType,
        frequencyTypeSnapshot,
        batchRunKey,
        frozenAt);
    snapshot.status = DailySettlementStatus.FAILED;
    snapshot.retryCount = 1;
    snapshot.totalParticipants = 0;
    snapshot.totalRecognizedSuccessCount = 0;
    snapshot.totalLockedAmount = 0L;
    snapshot.failureMessage = truncateFailureMessage(failureMessage);
    return snapshot;
  }

  private static DailySettlementSnapshot createSucceeded(
      DailySettlementPhase phase,
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
    snapshot.phase = Objects.requireNonNull(phase, "일일 정산 스냅샷 phase는 필수입니다.");
    snapshot.status = DailySettlementStatus.SUCCEEDED;
    snapshot.retryCount = 0;
    snapshot.batchRunKey = Objects.requireNonNull(batchRunKey, "배치 실행 키는 필수입니다.");
    snapshot.frozenAt = Objects.requireNonNull(frozenAt, "스냅샷 고정 시각은 필수입니다.");
    snapshot.totalParticipants = totalParticipants;
    snapshot.totalRecognizedSuccessCount = totalRecognizedSuccessCount;
    snapshot.totalLockedAmount = totalLockedAmount;
    return snapshot;
  }

  private void assignIdentity(
      DailySettlementPhase phase,
      Crew crew,
      LocalDate missionDate,
      DailySettlementType dailySettlementType,
      MissionFrequencyType frequencyTypeSnapshot,
      String batchRunKey,
      LocalDateTime frozenAt) {
    this.crew = Objects.requireNonNull(crew, "크루는 필수입니다.");
    this.missionDate = Objects.requireNonNull(missionDate, "미션 날짜는 필수입니다.");
    this.dailySettlementType = Objects.requireNonNull(dailySettlementType, "일일 정산 타입은 필수입니다.");
    this.frequencyTypeSnapshot = Objects.requireNonNull(frequencyTypeSnapshot, "미션 빈도 타입은 필수입니다.");
    this.phase = Objects.requireNonNull(phase, "일일 정산 스냅샷 phase는 필수입니다.");
    this.batchRunKey = Objects.requireNonNull(batchRunKey, "배치 실행 키는 필수입니다.");
    this.frozenAt = Objects.requireNonNull(frozenAt, "스냅샷 고정 시각은 필수입니다.");
  }

  private static String truncateFailureMessage(String failureMessage) {
    if (failureMessage == null) {
      return null;
    }
    if (failureMessage.length() <= FAILURE_MESSAGE_MAX_LENGTH) {
      return failureMessage;
    }
    return failureMessage.substring(0, FAILURE_MESSAGE_MAX_LENGTH);
  }

  private static void validateNonNegative(
      int totalParticipants, int totalRecognizedSuccessCount, long totalLockedAmount) {
    if (totalParticipants < 0 || totalRecognizedSuccessCount < 0 || totalLockedAmount < 0) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
  }
}
