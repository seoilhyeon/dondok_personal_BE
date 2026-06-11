package com.oit.dondok.infra.fcm.adapter;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.oit.dondok.domain.notification.port.NotificationPayload;
import com.oit.dondok.infra.fcm.event.FcmSendEvent;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

@ExtendWith(MockitoExtension.class)
class FcmSendEventListenerTest {

  @Mock private FirebaseMessaging firebaseMessaging;

  private static final NotificationPayload PAYLOAD =
      new NotificationPayload("CREW_CERT", "CREW", "42", "/crew/42", "미션 인증 완료!");

  private static final FcmSendEvent EVENT = new FcmSendEvent("device-token-xyz", PAYLOAD);

  private FcmSendEventListener listenerWithSyncExecutor() {
    return new FcmSendEventListener(firebaseMessaging, new SyncTaskExecutor());
  }

  @Test
  void onFcmSendHappyPathInvokesFirebaseSend() throws Exception {
    FcmSendEventListener sut = listenerWithSyncExecutor();
    given(firebaseMessaging.send(any(Message.class))).willReturn("projects/x/messages/1");

    assertThatCode(() -> sut.onFcmSend(EVENT)).doesNotThrowAnyException();

    verify(firebaseMessaging).send(any(Message.class));
  }

  @Test
  void onFcmSendExecutorSaturatedSwallowsRejectionAndSkipsSend() throws Exception {
    TaskExecutor saturatedExecutor = mock(TaskExecutor.class);
    doThrow(new TaskRejectedException("saturated", new RejectedExecutionException()))
        .when(saturatedExecutor)
        .execute(any());
    FcmSendEventListener sut = new FcmSendEventListener(firebaseMessaging, saturatedExecutor);

    assertThatCode(() -> sut.onFcmSend(EVENT)).doesNotThrowAnyException();

    verify(firebaseMessaging, never()).send(any(Message.class));
  }

  @Test
  void onFcmSendFirebaseMessagingExceptionSwallowsExceptionAndDoesNotPropagate() throws Exception {
    FcmSendEventListener sut = listenerWithSyncExecutor();
    FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
    given(firebaseMessaging.send(any(Message.class))).willThrow(exception);

    assertThatCode(() -> sut.onFcmSend(EVENT)).doesNotThrowAnyException();
  }
}
