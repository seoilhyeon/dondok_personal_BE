package com.oit.dondok.infra.fcm.adapter;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.oit.dondok.domain.notification.port.NotificationPayload;
import com.oit.dondok.infra.fcm.event.FcmSendEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// AFTER_COMMIT 시점에 fast-path로 FCM 발송을 시도한다.
// executor 포화 시 발송을 포기한다(best-effort per spec).
@Slf4j
@Component
@Profile("!test")
public class FcmSendEventListener {

  private final FirebaseMessaging firebaseMessaging;
  private final TaskExecutor fcmTaskExecutor;

  public FcmSendEventListener(
      FirebaseMessaging firebaseMessaging,
      @Qualifier("fcmTaskExecutor") TaskExecutor fcmTaskExecutor) {
    this.firebaseMessaging = firebaseMessaging;
    this.fcmTaskExecutor = fcmTaskExecutor;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onFcmSend(FcmSendEvent event) {
    String fcmToken = event.fcmToken();
    NotificationPayload payload = event.payload();
    try {
      fcmTaskExecutor.execute(() -> sendMessage(fcmToken, payload));
    } catch (TaskRejectedException e) {
      log.warn("[FCM] executor 포화로 발송 포기 token={}", fcmToken, e);
    }
  }

  private void sendMessage(String fcmToken, NotificationPayload payload) {
    Message message =
        Message.builder()
            .setToken(fcmToken)
            .setNotification(
                Notification.builder().setTitle("돈독").setBody(payload.displayText()).build())
            .putData("deep_link", payload.deepLink())
            .putData("event_type", payload.eventType())
            .putData("resource_type", payload.resourceType())
            .putData("resource_id", payload.resourceId())
            .build();
    try {
      String messageId = firebaseMessaging.send(message);
      log.debug("[FCM] 발송 성공 messageId={} token={}", messageId, fcmToken);
    } catch (FirebaseMessagingException e) {
      log.error("[FCM] 발송 실패 token={} errorCode={}", fcmToken, e.getMessagingErrorCode(), e);
    }
  }
}
