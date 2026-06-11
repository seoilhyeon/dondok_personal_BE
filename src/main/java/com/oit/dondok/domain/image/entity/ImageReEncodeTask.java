package com.oit.dondok.domain.image.entity;

import com.oit.dondok.global.entity.AuditableTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 미션 이미지 reEncode 작업을 위한 transactional outbox.
// createMissionLog가 mission_log save와 같은 트랜잭션에서 PENDING으로 적재하고,
// 커밋 후 즉시 시도/재처리 배치가 이 row를 집어 reEncode 후 상태를 전이한다.
@Getter
@Entity
@Table(
    name = "image_reencode_task",
    uniqueConstraints =
        @UniqueConstraint(name = "uq_irt_mission_log", columnNames = "mission_log_id"),
    indexes = @Index(name = "idx_irt_status_next_attempt", columnList = "status, next_attempt_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImageReEncodeTask extends AuditableTimeEntity {

  private static final int MAX_LAST_ERROR_LENGTH = 500;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "mission_log_id", nullable = false)
  private Long missionLogId;

  @Column(name = "s3_key", nullable = false)
  private String s3Key;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ReEncodeTaskStatus status;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(name = "last_error", length = 500)
  private String lastError;

  @Column(name = "next_attempt_at", nullable = false)
  private LocalDateTime nextAttemptAt;

  // claim fencing token. claim마다 증가하며, 결과 기록은 자신이 claim한 version일 때만 반영된다.
  // lease 만료 후 다른 워커가 reclaim하면 version이 올라가, 늦게 도착한 stale 결과를 무시할 수 있다.
  @Column(name = "attempt_version", nullable = false)
  private long attemptVersion;

  // 미션 로그 생성 트랜잭션에서 PENDING으로 적재
  public static ImageReEncodeTask pending(
      Long missionLogId, String s3Key, LocalDateTime enqueuedAt) {
    ImageReEncodeTask task = new ImageReEncodeTask();
    task.missionLogId = missionLogId;
    task.s3Key = s3Key;
    task.status = ReEncodeTaskStatus.PENDING;
    task.retryCount = 0;
    task.nextAttemptAt = enqueuedAt;
    return task;
  }

  // 작업을 선점한다. next_attempt_at을 lease만큼 미뤄 다른 워커의 재선점을 막고,
  // attempt_version을 올려 이번 claim에 대한 fencing token을 발급한다.
  public void claim(LocalDateTime leaseUntil) {
    this.nextAttemptAt = leaseUntil;
    this.attemptVersion++;
  }

  // 결과 기록 fencing: 아직 이 claim(version)이 소유 중이고 종결되지 않았을 때만 결과를 반영한다.
  // 다른 워커가 reclaim(version 상승)했거나 이미 DONE/FAILED면 stale 결과이므로 false.
  public boolean isCurrentAttempt(long attemptVersion) {
    return this.status == ReEncodeTaskStatus.PENDING && this.attemptVersion == attemptVersion;
  }

  // reEncode 성공: 종료 상태로 전이한다.
  public void markDone() {
    this.status = ReEncodeTaskStatus.DONE;
    this.lastError = null;
  }

  // reEncode 실패: 시도 횟수를 누적하고 마지막 사유를 남긴다.
  // maxRetryCount에 도달하면 FAILED로 종료, 아니면 다음 시도 시각을 예약해 PENDING으로 유지한다.
  public void recordFailure(String error, int maxRetryCount, LocalDateTime nextAttemptAt) {
    this.retryCount++;
    this.lastError = truncate(error);
    if (this.retryCount >= maxRetryCount) {
      this.status = ReEncodeTaskStatus.FAILED;
    } else {
      this.nextAttemptAt = nextAttemptAt;
    }
  }

  // 영구 실패: 같은 콘텐츠를 다시 처리해도 동일하게 실패하는 content-level 거절(예: 한도 초과 해상도,
  // 손상/미지원 이미지)은 재시도가 무의미하므로 retryCount/maxRetry와 무관하게 즉시 FAILED로 종결한다.
  public void recordPermanentFailure(String error) {
    this.retryCount++;
    this.lastError = truncate(error);
    this.status = ReEncodeTaskStatus.FAILED;
  }

  private static String truncate(String error) {
    if (error == null) {
      return null;
    }
    return error.length() <= MAX_LAST_ERROR_LENGTH
        ? error
        : error.substring(0, MAX_LAST_ERROR_LENGTH);
  }
}
