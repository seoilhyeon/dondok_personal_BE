package com.oit.dondok.infra.image.adapter;

import com.oit.dondok.domain.image.entity.ImageReEncodeTask;
import com.oit.dondok.domain.image.entity.ReEncodeTaskStatus;
import com.oit.dondok.domain.image.repository.ImageReEncodeTaskRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 즉시 시도가 유실(크래시)되거나 실패한 PENDING 작업을 주기적으로 재처리한다.
// 후보만 조회하고(잠금 없음) 실제 처리는 processor가 행 잠금으로 직렬화 → 긴 트랜잭션/락 보유 회피.
@Component
@RequiredArgsConstructor
public class ReEncodeTaskScheduler {

  private static final int BATCH_SIZE = 100;

  private final ImageReEncodeTaskRepository repository;
  private final ReEncodeTaskProcessor processor;

  @Scheduled(fixedDelayString = "${app.reencode.batch-delay-ms:60000}")
  public void retryPending() {
    List<ImageReEncodeTask> due =
        repository.findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAt(
            ReEncodeTaskStatus.PENDING, LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE));
    for (ImageReEncodeTask task : due) {
      processor.process(task.getId());
    }
  }
}
