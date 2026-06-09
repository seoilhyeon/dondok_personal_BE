package com.oit.dondok.infra.image.adapter;

import com.oit.dondok.domain.image.entity.ImageReEncodeTask;
import com.oit.dondok.domain.image.repository.ImageReEncodeTaskClaimRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 즉시 시도가 유실(크래시)되거나 실패한 PENDING 작업을 주기적으로 재처리한다.
// claim 단계(SKIP LOCKED + lease)로 다중 워커가 겹치지 않게 선점한 뒤, 락 밖에서 건별 처리한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class ReEncodeTaskScheduler {

  private static final int BATCH_SIZE = 100;

  private final ImageReEncodeTaskClaimRepository claimRepository;
  private final ReEncodeTaskProcessor processor;

  @Scheduled(fixedDelayString = "${app.reencode.batch-delay-ms:60000}")
  public void retryPending() {
    List<ImageReEncodeTask> claimed =
        claimRepository.claimPendingTasks(LocalDateTime.now(), BATCH_SIZE);
    for (ImageReEncodeTask task : claimed) {
      try {
        processor.process(task);
      } catch (RuntimeException e) {
        // 한 작업의 예외(커밋 실패 등)가 같은 사이클의 나머지 작업을 막지 않도록 격리한다.
        // 처리하지 못한 작업은 lease 만료 후 다음 주기에 재선점된다.
        log.warn("reEncode 작업 처리 실패, 다음 주기에 재시도 taskId={}", task.getId(), e);
      }
    }
  }
}
