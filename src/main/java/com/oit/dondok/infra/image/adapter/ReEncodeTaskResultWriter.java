package com.oit.dondok.infra.image.adapter;

import com.oit.dondok.domain.image.entity.ImageReEncodeTask;
import com.oit.dondok.domain.image.entity.ReEncodeTaskStatus;
import com.oit.dondok.domain.image.repository.ImageReEncodeTaskRepository;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.ErrorCode;
import com.oit.dondok.infra.image.exception.ImageErrorCode;
import java.time.LocalDateTime;
import java.util.Set;
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

  // 같은 콘텐츠를 다시 처리해도 동일하게 실패하는 content-level 거절. 재시도가 무의미하므로 즉시 FAILED로 종결한다.
  // (IMAGE_NOT_FOUND 등 그 외 실패는 일시적일 수 있어 transient로 보고 재시도한다.)
  private static final Set<ErrorCode> PERMANENT_FAILURES =
      Set.of(
          ImageErrorCode.IMAGE_READ_FAILED,
          ImageErrorCode.IMAGE_ENCODE_FAILED,
          ImageErrorCode.UNSUPPORTED_IMAGE_TYPE,
          ImageErrorCode.IMAGE_TOO_LARGE,
          ImageErrorCode.IMAGE_DIMENSIONS_TOO_LARGE,
          ImageErrorCode.EMPTY_IMAGE);

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
              if (isPermanentFailure(cause)) {
                // 영구 거절: 재시도하지 않고 즉시 FAILED. (대부분 제출 시점에 차단되므로 여기 도달은 드물다)
                task.recordPermanentFailure(describe(cause));
                log.error(
                    "reEncode 영구 실패(재시도 안 함) missionLogId={} s3Key={} reason={}",
                    task.getMissionLogId(),
                    task.getS3Key(),
                    describe(cause),
                    cause);
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

  // content-level 거절(재처리해도 동일 실패)인지 판별한다. transient 처리 실패와 구분해 무의미한 재시도를 막는다.
  private static boolean isPermanentFailure(Throwable cause) {
    return cause instanceof CustomException customException
        && PERMANENT_FAILURES.contains(customException.getErrorCode());
  }

  private static String describe(Throwable cause) {
    String message = cause.getMessage();
    return message != null ? message : cause.getClass().getSimpleName();
  }
}
