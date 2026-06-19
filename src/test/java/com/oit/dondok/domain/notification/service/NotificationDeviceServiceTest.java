package com.oit.dondok.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.domain.notification.dto.request.RegisterDeviceRequest;
import com.oit.dondok.domain.notification.dto.response.RegisterDeviceResponse;
import com.oit.dondok.domain.notification.entity.NotificationDevice;
import com.oit.dondok.domain.notification.entity.NotificationPlatform;
import com.oit.dondok.domain.notification.repository.NotificationDeviceRepository;
import com.oit.dondok.global.exception.CustomException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationDeviceServiceTest {

  @Mock private MemberRepository memberRepository;
  @Mock private NotificationDeviceRepository notificationDeviceRepository;

  @InjectMocks private NotificationDeviceService notificationDeviceService;

  private static final UUID MEMBER_UUID = UUID.randomUUID();
  private static final String DEVICE_ID = "device-abc-123";
  private static final String FCM_TOKEN = "fcm-token-xyz";

  @Test
  void registerDeviceNewDeviceSavesAndReturnsPayload() {
    Member member = Member.create("test@example.com", null, "테스터");
    NotificationDevice saved =
        NotificationDevice.create(member, DEVICE_ID, NotificationPlatform.WEB, FCM_TOKEN, "1.0.0");
    ReflectionTestUtils.setField(saved, "createdAt", java.time.LocalDateTime.now());

    given(memberRepository.findByUuid(MEMBER_UUID)).willReturn(Optional.of(member));
    given(notificationDeviceRepository.findByMemberAndDeviceId(member, DEVICE_ID))
        .willReturn(Optional.empty());
    given(notificationDeviceRepository.save(any(NotificationDevice.class))).willReturn(saved);

    RegisterDeviceRequest request =
        new RegisterDeviceRequest(NotificationPlatform.WEB, FCM_TOKEN, DEVICE_ID, "1.0.0");

    RegisterDeviceResponse response =
        notificationDeviceService.registerDevice(MEMBER_UUID, request);

    assertThat(response.deviceId()).isEqualTo(DEVICE_ID);
    assertThat(response.platform()).isEqualTo(NotificationPlatform.WEB);
    assertThat(response.enabled()).isTrue();
    then(notificationDeviceRepository).should().save(any(NotificationDevice.class));
  }

  @Test
  void registerDeviceExistingDeviceUpdatesTokenWithoutInsert() {
    Member member = Member.create("test@example.com", null, "테스터");
    NotificationDevice existing =
        NotificationDevice.create(
            member, DEVICE_ID, NotificationPlatform.WEB, "old-token", "0.9.0");
    ReflectionTestUtils.setField(existing, "createdAt", java.time.LocalDateTime.now());

    given(memberRepository.findByUuid(MEMBER_UUID)).willReturn(Optional.of(member));
    given(notificationDeviceRepository.findByMemberAndDeviceId(member, DEVICE_ID))
        .willReturn(Optional.of(existing));

    RegisterDeviceRequest request =
        new RegisterDeviceRequest(NotificationPlatform.WEB, FCM_TOKEN, DEVICE_ID, "1.0.0");

    RegisterDeviceResponse response =
        notificationDeviceService.registerDevice(MEMBER_UUID, request);

    assertThat(response.deviceId()).isEqualTo(DEVICE_ID);
    assertThat(existing.getFcmToken()).isEqualTo(FCM_TOKEN);
    then(notificationDeviceRepository).shouldHaveNoMoreInteractions();
  }

  @Test
  void registerDeviceUnknownMemberThrowsNotFound() {
    given(memberRepository.findByUuid(MEMBER_UUID)).willReturn(Optional.empty());

    RegisterDeviceRequest request =
        new RegisterDeviceRequest(NotificationPlatform.WEB, FCM_TOKEN, DEVICE_ID, null);

    assertThatThrownBy(() -> notificationDeviceService.registerDevice(MEMBER_UUID, request))
        .isInstanceOf(CustomException.class);
  }
}
