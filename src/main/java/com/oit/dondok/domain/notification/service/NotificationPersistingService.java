package com.oit.dondok.domain.notification.service;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.notification.entity.Notification;
import com.oit.dondok.domain.notification.port.NotificationPayload;
import com.oit.dondok.domain.notification.port.NotificationSender;
import com.oit.dondok.domain.notification.repository.NotificationRepository;
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

  private final NotificationRepository notificationRepository;

  // FCM 또는 Stub — 프로파일·credentials 없으면 null(푸시 없이 인박스만 저장)
  @Autowired(required = false)
  @Qualifier("push")
  private NotificationSender pushSender;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void send(Member member, NotificationPayload payload) {
    notificationRepository.save(Notification.create(member, payload));
    if (pushSender != null) {
      pushSender.send(member, payload);
    } else {
      log.debug("[Notification] 푸시 발송 생략(push sender 없음) eventType={}", payload.eventType());
    }
  }
}
