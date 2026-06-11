package com.oit.dondok.infra.fcm.adapter;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.notification.entity.NotificationDevice;
import com.oit.dondok.domain.notification.port.NotificationPayload;
import com.oit.dondok.domain.notification.port.NotificationSender;
import com.oit.dondok.domain.notification.repository.NotificationDeviceRepository;
import com.oit.dondok.infra.fcm.event.FcmSendEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// 호출자 트랜잭션에 참여해 디바이스 조회 후 FcmSendEvent를 발행한다.
// 실제 FCM 발송은 AFTER_COMMIT에 executor 스레드에서 처리된다(best-effort).
@Slf4j
@Component
@Profile("!test & !integration")
@RequiredArgsConstructor
public class FcmNotificationSenderAdapter implements NotificationSender {

  private final NotificationDeviceRepository notificationDeviceRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  public void send(Member member, NotificationPayload payload) {
    List<NotificationDevice> devices =
        notificationDeviceRepository.findByMemberAndEnabledTrue(member);
    if (devices.isEmpty()) {
      log.debug("[FCM] 활성 디바이스 없음 memberUuid={}", member.getUuid());
      return;
    }
    for (NotificationDevice device : devices) {
      eventPublisher.publishEvent(new FcmSendEvent(device.getFcmToken(), payload));
    }
  }
}
