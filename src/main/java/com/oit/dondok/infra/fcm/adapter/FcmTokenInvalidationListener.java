package com.oit.dondok.infra.fcm.adapter;

import com.oit.dondok.domain.notification.repository.NotificationDeviceQueryRepository;
import com.oit.dondok.infra.fcm.event.FcmTokenInvalidatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile("!test & !integration")
@RequiredArgsConstructor
public class FcmTokenInvalidationListener {

  private final NotificationDeviceQueryRepository notificationDeviceQueryRepository;

  @Async("fcmTaskExecutor")
  @EventListener
  @Transactional
  public void onTokenInvalidated(FcmTokenInvalidatedEvent event) {
    int updated = notificationDeviceQueryRepository.disableByFcmToken(event.fcmToken());
    log.info("[FCM] 만료 토큰 비활성화 완료 token={} updated={}", maskToken(event.fcmToken()), updated);
  }

  private static String maskToken(String token) {
    if (token == null || token.length() < 8) {
      return "****";
    }
    return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
  }
}
