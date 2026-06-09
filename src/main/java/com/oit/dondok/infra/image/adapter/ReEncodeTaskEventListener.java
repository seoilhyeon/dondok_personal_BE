package com.oit.dondok.infra.image.adapter;

import com.oit.dondok.domain.image.repository.ImageReEncodeTaskClaimRepository;
import com.oit.dondok.infra.image.event.ReEncodeTaskCreatedEvent;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 커밋 직후 fast-path. 요청 스레드는 전용 executor에 제출만 하고, claim/reEncode/finalize는 executor 스레드에서 수행한다.
// executor 포화로 제출이 거부돼도 이미 커밋된 task는 배치 backstop이 처리하므로, best-effort로 무시해 요청 성공을 지킨다.
@Slf4j
@Component
public class ReEncodeTaskEventListener {

  private final ImageReEncodeTaskClaimRepository claimRepository;
  private final ReEncodeTaskProcessor processor;
  private final TaskExecutor reEncodeTaskExecutor;

  // TaskExecutor 빈이 여러 개(예: Spring Boot 기본 applicationTaskExecutor)일 수 있으므로 전용 빈을 qualifier로 명시한다.
  public ReEncodeTaskEventListener(
      ImageReEncodeTaskClaimRepository claimRepository,
      ReEncodeTaskProcessor processor,
      @Qualifier("reEncodeTaskExecutor") TaskExecutor reEncodeTaskExecutor) {
    this.claimRepository = claimRepository;
    this.processor = processor;
    this.reEncodeTaskExecutor = reEncodeTaskExecutor;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCreated(ReEncodeTaskCreatedEvent event) {
    Long taskId = event.taskId();
    try {
      reEncodeTaskExecutor.execute(() -> claimAndProcess(taskId));
    } catch (TaskRejectedException e) {
      // executor 포화: fast-path 포기. 배치가 재처리하므로 요청 성공 응답을 깨지 않는다.
      log.warn("reEncode fast-path 제출 거부, 배치 재처리 예정 taskId={}", taskId, e);
    }
  }

  private void claimAndProcess(Long taskId) {
    claimRepository.claimById(taskId, LocalDateTime.now()).ifPresent(processor::process);
  }
}
