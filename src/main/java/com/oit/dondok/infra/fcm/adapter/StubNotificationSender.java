package com.oit.dondok.infra.fcm.adapter;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.notification.port.NotificationPayload;
import com.oit.dondok.domain.notification.port.NotificationSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("test")
public class StubNotificationSender implements NotificationSender {

  @Override
  public void send(Member member, NotificationPayload payload) {
    log.debug("[FCM-STUB] 발송 생략 memberUuid={} eventType={}", member.getUuid(), payload.eventType());
  }
}
