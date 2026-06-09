package com.oit.dondok.infra.image.adapter;

import com.oit.dondok.domain.image.entity.ImageReEncodeTask;
import com.oit.dondok.domain.image.port.ReEncodeTaskEnqueuePort;
import com.oit.dondok.domain.image.repository.ImageReEncodeTaskRepository;
import com.oit.dondok.infra.image.event.ReEncodeTaskCreatedEvent;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

// PENDING 작업을 호출자(createMissionLog)의 트랜잭션에 적재하고,
// AFTER_COMMIT 즉시 시도를 위한 이벤트를 발행한다.
// 별도 @Transactional을 두지 않아 호출자 트랜잭션(REQUIRED)에 그대로 참여 -> mission_log와 같은 커밋 단위.
@Component
@RequiredArgsConstructor
public class ReEncodeTaskEnqueueAdapter implements ReEncodeTaskEnqueuePort {

  private final ImageReEncodeTaskRepository repository;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  public void enqueue(Long missionLogId, String s3Key) {
    ImageReEncodeTask task =
        repository.save(ImageReEncodeTask.pending(missionLogId, s3Key, LocalDateTime.now()));
    // AFTER_COMMIT 리스너는 커밋된 task를 보장받아야 하므로, 발행만 하고 처리는 커밋 이후로 미룬다.
    eventPublisher.publishEvent(new ReEncodeTaskCreatedEvent(task.getId()));
  }
}
