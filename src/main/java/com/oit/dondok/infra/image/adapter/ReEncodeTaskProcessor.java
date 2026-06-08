package com.oit.dondok.infra.image.adapter;

import com.oit.dondok.domain.image.entity.ImageReEncodeTask;
import com.oit.dondok.domain.image.entity.ReEncodeTaskStatus;
import com.oit.dondok.domain.image.repository.ImageReEncodeTaskRepository;
import com.oit.dondok.domain.mission.port.ImageProcessingPort;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 단일 reEncode 작업을 처리한다. findById가 PESSIMISTIC_WRITE로 행을 잠그고 status guard로 멱등 처리한다.
// reEncode idempotent(이미 EXIF 없는 이미지 재인코딩도 안전)하므로 드문 중복 실행에도 무해하다.
@Component
@RequiredArgsConstructor
public class ReEncodeTaskProcessor {

  private static final Logger log = LoggerFactory.getLogger(ReEncodeTaskProcessor.class);
  private static final int MAX_RETRY = 3; // 3회 재시도 후 FAILED
  private static final long BACKOFF_BASE_MINUTES = 2;

  private final ImageReEncodeTaskRepository repository;
  private final ImageProcessingPort imageProcessingPort;

  @Transactional
  public void process(Long taskId) {
    ImageReEncodeTask task = repository.findById(taskId).orElse(null);
    if (task == null || task.getStatus() != ReEncodeTaskStatus.PENDING) {
      return; // 이미 처리됨(DONE/FAILED)이거나 사라진 작업
    }
    try {
      imageProcessingPort.reEncode(task.getS3Key());
      task.markDone();
    } catch (RuntimeException e) {
      LocalDateTime nextAttempt =
          LocalDateTime.now().plusMinutes(BACKOFF_BASE_MINUTES * (task.getRetryCount() + 1L));
      task.recordFailure(describe(e), MAX_RETRY, nextAttempt);
      if (task.getStatus() == ReEncodeTaskStatus.FAILED) {
        // 최종 실패 -> Grafana 알림 대상
        log.error(
            "reEncode 최종 실패 missionLogId={} s3Key={} retries={}",
            task.getMissionLogId(),
            task.getS3Key(),
            task.getRetryCount(),
            e);
      } else {
        log.warn(
            "reEncode 실패, 재시도 예정 missionLogId={} retries={} nextAttemptAt={}",
            task.getMissionLogId(),
            task.getRetryCount(),
            nextAttempt);
      }
    }
  }

  private static String describe(RuntimeException e) {
    String message = e.getMessage();
    return message != null ? message : e.getClass().getSimpleName();
  }
}
