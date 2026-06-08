package com.oit.dondok.infra.image.adapter;

import com.oit.dondok.domain.image.entity.ImageReEncodeTask;
import com.oit.dondok.domain.image.repository.ImageReEncodeTaskClaimRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 즉시 시도가 유실(크래시)되거나 실패한 PENDING 작업을 주기적으로 재처리한다.
// claim 단계(SKIP LOCKED + lease)로 다중 워커가 겹치지 않게 선점한 뒤, 락 밖에서 건별 처리한다.
@Component
@RequiredArgsConstructor
public class ReEncodeTaskScheduler {

  private static final int BATCH_SIZE = 100;
  // 선점 후 처리(S3 왕복)가 끝날 때까지 다른 워커의 재선점을 막는 lease. 워커가 죽으면 이 시간 후 회수된다.
  private static final Duration LEASE = Duration.ofMinutes(5);

  private final ImageReEncodeTaskClaimRepository claimRepository;
  private final ReEncodeTaskProcessor processor;

  @Scheduled(fixedDelayString = "${app.reencode.batch-delay-ms:60000}")
  public void retryPending() {
    LocalDateTime now = LocalDateTime.now();
    List<ImageReEncodeTask> claimed =
        claimRepository.claimPendingTasks(now, now.plus(LEASE), BATCH_SIZE);
    for (ImageReEncodeTask task : claimed) {
      processor.process(task.getId());
    }
  }
}
