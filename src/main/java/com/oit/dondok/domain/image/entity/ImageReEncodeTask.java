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

  private static String truncate(String error) {
    if (error == null) {
      return null;
    }
    return error.length() <= MAX_LAST_ERROR_LENGTH
        ? error
        : error.substring(0, MAX_LAST_ERROR_LENGTH);
  }
}
