package com.oit.dondok.infra.image.adapter;

import com.oit.dondok.domain.image.entity.ImageReEncodeTask;
import com.oit.dondok.domain.image.entity.ReEncodeTaskStatus;
import com.oit.dondok.domain.image.repository.ImageReEncodeTaskRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// reEncode 처리 결과만 짧은 트랜잭션으로 기록한다. S3 왕복(락/커넥션 점유)과 분리하기 위한 finalize 단계.
@Slf4j
@Component
@RequiredArgsConstructor
public class ReEncodeTaskResultWriter {

  private static final int MAX_RETRY = 3; // 3회 재시도 후 FAILED
  private static final long BACKOFF_BASE_MINUTES = 2;

  private final ImageReEncodeTaskRepository repository;

  @Transactional
  public void complete(Long taskId, long attemptVersion) {
    repository
        .findById(taskId)
        .ifPresent(
            task -> {
              if (!task.isCurrentAttempt(attemptVersion)) {
                logStaleResult("complete", taskId, attemptVersion, task);
                return;
              }
              task.markDone();
            });
  }

  @Transactional
  public void fail(Long taskId, long attemptVersion, Throwable cause) {
    repository
        .findById(taskId)
        .ifPresent(
            task -> {
              if (!task.isCurrentAttempt(attemptVersion)) {
                logStaleResult("fail", taskId, attemptVersion, task);
                return;
              }
              LocalDateTime nextAttempt =
                  LocalDateTime.now()
                      .plusMinutes(BACKOFF_BASE_MINUTES * (task.getRetryCount() + 1L));
              task.recordFailure(describe(cause), MAX_RETRY, nextAttempt);
              if (task.getStatus() == ReEncodeTaskStatus.FAILED) {
                // 최종 실패 -> Grafana 알림 대상
                log.error(
                    "reEncode 최종 실패 missionLogId={} s3Key={} retries={}",
                    task.getMissionLogId(),
                    task.getS3Key(),
                    task.getRetryCount(),
                    cause);
              } else {
                log.warn(
                    "reEncode 실패, 재시도 예정 missionLogId={} retries={} nextAttemptAt={}",
                    task.getMissionLogId(),
                    task.getRetryCount(),
                    nextAttempt,
                    cause);
              }
            });
  }

  // 이미 reclaim(version 상승)됐거나 DONE/FAILED로 종결된 작업에 늦게 도착한 결과는 무시한다.
  private void logStaleResult(
      String phase, Long taskId, long attemptVersion, ImageReEncodeTask task) {
    log.warn(
        "stale reEncode 결과 무시 phase={} taskId={} attemptVersion={} 현재버전={} status={}",
        phase,
        taskId,
        attemptVersion,
        task.getAttemptVersion(),
        task.getStatus());
  }

  private static String describe(Throwable cause) {
    String message = cause.getMessage();
    return message != null ? message : cause.getClass().getSimpleName();
  }
}
