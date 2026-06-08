package com.oit.dondok.infra.image.adapter;

import static org.mockito.Mockito.verify;

import com.oit.dondok.infra.image.event.ReEncodeTaskCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReEncodeTaskEventListenerTest {

  @Mock private ReEncodeTaskProcessor processor;

  @InjectMocks private ReEncodeTaskEventListener listener;

  // AFTER_COMMIT 이벤트 수신 시 이벤트의 taskId로 processor에 처리를 위임한다.
  @Test
  void delegatesToProcessor() {
    listener.onCreated(new ReEncodeTaskCreatedEvent(7L));

    verify(processor).process(7L);
  }
}
