package com.oit.dondok.infra.image.adapter;

import com.oit.dondok.infra.image.event.ReEncodeTaskCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 커밋, 커넥션 반납 이후 별도 스레드에서 reEncode를 즉시 시도한다(요청 스레드/커넥션 비점유).
// 실패하거나 이 스레드가 유실되어도 task는 PENDING으로 남아 배치가 backstop으로 처리한다.
@Component
@RequiredArgsConstructor
public class ReEncodeTaskEventListener {

  private final ReEncodeTaskProcessor processor;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCreated(ReEncodeTaskCreatedEvent event) {
    processor.process(event.taskId());
  }
}
