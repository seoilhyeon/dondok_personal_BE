package com.oit.dondok.infra.image.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.oit.dondok.domain.image.entity.ImageReEncodeTask;
import com.oit.dondok.domain.image.entity.ReEncodeTaskStatus;
import com.oit.dondok.domain.image.repository.ImageReEncodeTaskRepository;
import com.oit.dondok.infra.image.event.ReEncodeTaskCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReEncodeTaskEnqueueAdapterTest {

  private static final Long MISSION_LOG_ID = 10L;
  private static final Long TASK_ID = 99L;
  private static final String OBJECT_PATH = "mission/42/101/abc";

  @Mock private ImageReEncodeTaskRepository repository;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private ReEncodeTaskEnqueueAdapter adapter;

  // PENDING 작업을 저장하고, 저장된 id로 생성 이벤트를 발행한다.
  @Test
  void savesPendingTaskAndPublishesEventWithSavedId() {
    given(repository.save(any(ImageReEncodeTask.class)))
        .willAnswer(
            invocation -> {
              ImageReEncodeTask task = invocation.getArgument(0);
              ReflectionTestUtils.setField(task, "id", TASK_ID); // DB가 채울 PK를 흉내
              return task;
            });

    adapter.enqueue(MISSION_LOG_ID, OBJECT_PATH);

    ArgumentCaptor<ImageReEncodeTask> savedCaptor =
        ArgumentCaptor.forClass(ImageReEncodeTask.class);
    verify(repository).save(savedCaptor.capture());
    ImageReEncodeTask saved = savedCaptor.getValue();
    assertThat(saved.getMissionLogId()).isEqualTo(MISSION_LOG_ID);
    assertThat(saved.getS3Key()).isEqualTo(OBJECT_PATH);
    assertThat(saved.getStatus()).isEqualTo(ReEncodeTaskStatus.PENDING);

    verify(eventPublisher).publishEvent(new ReEncodeTaskCreatedEvent(TASK_ID));
  }
}
