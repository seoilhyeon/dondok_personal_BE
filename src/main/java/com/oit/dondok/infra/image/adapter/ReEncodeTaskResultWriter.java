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
  public void complete(Long taskId) {
    repository.findById(taskId).ifPresent(ImageReEncodeTask::markDone);
  }

  @Transactional
  public void fail(Long taskId, Throwable cause) {
    repository
        .findById(taskId)
        .ifPresent(
            task -> {
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

  private static String describe(Throwable cause) {
    String message = cause.getMessage();
    return message != null ? message : cause.getClass().getSimpleName();
  }
}
