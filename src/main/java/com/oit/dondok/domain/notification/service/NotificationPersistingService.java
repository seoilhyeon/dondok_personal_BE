package com.oit.dondok.domain.notification.service;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.notification.entity.Notification;
import com.oit.dondok.domain.notification.entity.NotificationCategory;
import com.oit.dondok.domain.notification.port.NotificationPayload;
import com.oit.dondok.domain.notification.port.NotificationSender;
import com.oit.dondok.domain.notification.repository.NotificationRepository;
import com.oit.dondok.domain.notification.repository.NotificationSettingsRepository;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class NotificationPersistingService implements NotificationSender {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

  private final NotificationRepository notificationRepository;
  private final NotificationSettingsRepository notificationSettingsRepository;

  // FCM 또는 Stub — 프로파일·credentials 없으면 null(푸시 없이 인박스만 저장)
  @Autowired(required = false)
  @Qualifier("push")
  private NotificationSender pushSender;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void send(Member member, NotificationPayload payload) {
    if (isBlocked(member, payload.eventType())) {
      log.debug(
          "[Notification] 카테고리 설정으로 발송 차단 memberUuid={} eventType={}",
          member.getUuid(),
          payload.eventType());
      return;
    }
    notificationRepository.save(Notification.create(member, payload));
    if (pushSender != null) {
      pushSender.send(member, payload);
    } else {
      log.debug("[Notification] 푸시 발송 생략(push sender 없음) eventType={}", payload.eventType());
    }
  }

  private boolean isBlocked(Member member, String eventType) {
    Set<NotificationCategory> categories = NotificationCategory.forEventType(eventType);
    return notificationSettingsRepository
        .findByMemberUuid(member.getUuid())
        .map(
            settings -> {
              if (settings.isInQuietHours(LocalTime.now(SEOUL_ZONE))) {
                return true;
              }
              if (categories.isEmpty()) {
                return false;
              }
              Map<NotificationCategory, Boolean> map = settings.categoryMap();
              return categories.stream().anyMatch(c -> Boolean.FALSE.equals(map.get(c)));
            })
        .orElse(false);
  }
}
