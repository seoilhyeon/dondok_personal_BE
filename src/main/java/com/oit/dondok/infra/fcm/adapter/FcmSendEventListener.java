package com.oit.dondok.infra.fcm.adapter;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.oit.dondok.domain.notification.port.NotificationPayload;
import com.oit.dondok.infra.fcm.event.FcmSendEvent;
import com.oit.dondok.infra.fcm.event.FcmTokenInvalidatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ApplicationEventPublisher;
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
@Profile("!test & !integration")
@ConditionalOnExpression(
    "T(org.springframework.util.StringUtils).hasText('${app.firebase.credentials-path:}')")
public class FcmSendEventListener {

  private final FirebaseMessaging firebaseMessaging;
  private final TaskExecutor fcmTaskExecutor;
  private final ApplicationEventPublisher eventPublisher;

  public FcmSendEventListener(
      FirebaseMessaging firebaseMessaging,
      @Qualifier("fcmTaskExecutor") TaskExecutor fcmTaskExecutor,
      ApplicationEventPublisher eventPublisher) {
    this.firebaseMessaging = firebaseMessaging;
    this.fcmTaskExecutor = fcmTaskExecutor;
    this.eventPublisher = eventPublisher;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onFcmSend(FcmSendEvent event) {
    String fcmToken = event.fcmToken();
    NotificationPayload payload = event.payload();
    try {
      fcmTaskExecutor.execute(() -> sendMessage(fcmToken, payload));
    } catch (TaskRejectedException e) {
      log.warn("[FCM] executor 포화로 발송 포기 token={}", maskToken(fcmToken), e);
    }
  }

  private void sendMessage(String fcmToken, NotificationPayload payload) {
    try {
      String body = payload.displayText();
      if (body != null && body.length() > 1000) {
        body = body.substring(0, 1000);
      }
      Message message =
          Message.builder()
              .setToken(fcmToken)
              .putData("title", "돈독")
              .putData("body", body != null ? body : "")
              .putData("deep_link", payload.deepLink())
              .putData("event_type", payload.eventType())
              .putData("resource_type", payload.resourceType())
              .putData("resource_id", payload.resourceId())
              .build();
      String messageId = firebaseMessaging.send(message);
      log.debug("[FCM] 발송 성공 messageId={} token={}", messageId, maskToken(fcmToken));
    } catch (FirebaseMessagingException e) {
      if (MessagingErrorCode.UNREGISTERED.equals(e.getMessagingErrorCode())) {
        log.warn("[FCM] 토큰 만료(UNREGISTERED), 비활성화 이벤트 발행 token={}", maskToken(fcmToken));
        try {
          eventPublisher.publishEvent(new FcmTokenInvalidatedEvent(fcmToken));
        } catch (RuntimeException ex) {
          log.error("[FCM] 토큰 비활성화 이벤트 발행 실패 token={}", maskToken(fcmToken), ex);
        }
      } else {
        log.error(
            "[FCM] 발송 실패 token={} errorCode={}", maskToken(fcmToken), e.getMessagingErrorCode(), e);
      }
    } catch (Exception e) {
      log.error("[FCM] 예상치 못한 오류 token={}", maskToken(fcmToken), e);
    }
  }

  private static String maskToken(String token) {
    if (token == null || token.length() < 8) {
      return "****";
    }
    return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
  }
}
