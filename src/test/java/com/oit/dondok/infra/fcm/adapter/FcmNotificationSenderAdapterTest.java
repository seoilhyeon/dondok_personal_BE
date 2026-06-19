package com.oit.dondok.infra.fcm.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.notification.entity.NotificationDevice;
import com.oit.dondok.domain.notification.port.NotificationPayload;
import com.oit.dondok.domain.notification.repository.NotificationDeviceRepository;
import com.oit.dondok.infra.fcm.event.FcmSendEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class FcmNotificationSenderAdapterTest {

  @Mock private NotificationDeviceRepository notificationDeviceRepository;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private FcmNotificationSenderAdapter sut;

  private static final NotificationPayload PAYLOAD =
      new NotificationPayload("CREW_CERT", "CREW", "42", "/crew/42", "미션 인증 완료!", null);

  private static Member member() {
    return Member.create("test@example.com", null, "테스터");
  }

  private static NotificationDevice deviceWithToken(String token) {
    NotificationDevice device = mock(NotificationDevice.class);
    given(device.getFcmToken()).willReturn(token);
    return device;
  }

  @Test
  void sendNoDevicesPublishesNoEvent() {
    Member member = member();
    given(notificationDeviceRepository.findByMemberAndEnabledTrue(member)).willReturn(List.of());

    sut.send(member, PAYLOAD);

    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void sendSingleDevicePublishesEventWithCorrectTokenAndPayload() {
    Member member = member();
    NotificationDevice device = deviceWithToken("token-abc");
    given(notificationDeviceRepository.findByMemberAndEnabledTrue(member))
        .willReturn(List.of(device));

    sut.send(member, PAYLOAD);

    ArgumentCaptor<FcmSendEvent> captor = ArgumentCaptor.forClass(FcmSendEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    FcmSendEvent event = captor.getValue();
    assertThat(event.fcmToken()).isEqualTo("token-abc");
    assertThat(event.payload()).isEqualTo(PAYLOAD);
  }

  @Test
  void sendMultipleDevicesPublishesOneEventPerDevice() {
    Member member = member();
    NotificationDevice d1 = deviceWithToken("token-1");
    NotificationDevice d2 = deviceWithToken("token-2");
    given(notificationDeviceRepository.findByMemberAndEnabledTrue(member))
        .willReturn(List.of(d1, d2));

    sut.send(member, PAYLOAD);

    ArgumentCaptor<FcmSendEvent> captor = ArgumentCaptor.forClass(FcmSendEvent.class);
    verify(eventPublisher, times(2)).publishEvent(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(FcmSendEvent::fcmToken)
        .containsExactly("token-1", "token-2");
    assertThat(captor.getAllValues()).extracting(FcmSendEvent::payload).containsOnly(PAYLOAD);
  }
}
