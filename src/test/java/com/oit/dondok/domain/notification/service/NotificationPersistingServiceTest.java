package com.oit.dondok.domain.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.notification.entity.NotificationCategory;
import com.oit.dondok.domain.notification.entity.NotificationSettings;
import com.oit.dondok.domain.notification.port.NotificationPayload;
import com.oit.dondok.domain.notification.port.NotificationSender;
import com.oit.dondok.domain.notification.repository.NotificationRepository;
import com.oit.dondok.domain.notification.repository.NotificationSettingsRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationPersistingServiceTest {

  @Mock private NotificationRepository notificationRepository;
  @Mock private NotificationSettingsRepository notificationSettingsRepository;
  @Mock private NotificationSender pushSender;

  @InjectMocks private NotificationPersistingService notificationPersistingService;

  private Member member;

  @BeforeEach
  void setUp() {
    member = Member.create("test@example.com", null, "테스터");
    ReflectionTestUtils.setField(notificationPersistingService, "pushSender", pushSender);
  }

  @Test
  void sendSavesAndPushesWhenCategoryEnabled() {
    NotificationSettings settings = NotificationSettings.createDefault(member);
    given(notificationSettingsRepository.findByMemberUuid(any(UUID.class)))
        .willReturn(Optional.of(settings));

    NotificationPayload payload = payload("FEED_REACTION_ADDED");

    notificationPersistingService.send(member, payload);

    then(notificationRepository).should().save(any());
    then(pushSender).should().send(member, payload);
  }

  @Test
  void sendSkipsWhenCategoryDisabled() {
    Member m = Member.create("test@example.com", null, "테스터");
    NotificationSettings settings = NotificationSettings.createDefault(m);
    settings.update(Map.of(NotificationCategory.EMOJI_REACTION, false), null, null);

    given(notificationSettingsRepository.findByMemberUuid(any(UUID.class)))
        .willReturn(Optional.of(settings));

    notificationPersistingService.send(m, payload("FEED_REACTION_ADDED"));

    then(notificationRepository).should(never()).save(any());
    then(pushSender).should(never()).send(any(), any());
  }

  @Test
  void sendSkipsWhenAnyMappedCategoryDisabled() {
    // CREW_NOTICE_REACTION_ADDED → CREW_NEWS + EMOJI_REACTION
    // CREW_NEWS 비활성화만으로도 차단
    Member m = Member.create("test@example.com", null, "테스터");
    NotificationSettings settings = NotificationSettings.createDefault(m);
    settings.update(Map.of(NotificationCategory.CREW_NEWS, false), null, null);

    given(notificationSettingsRepository.findByMemberUuid(any(UUID.class)))
        .willReturn(Optional.of(settings));

    notificationPersistingService.send(m, payload("CREW_NOTICE_REACTION_ADDED"));

    then(notificationRepository).should(never()).save(any());
  }

  @Test
  void sendAllowedWhenNoSettingsRow() {
    given(notificationSettingsRepository.findByMemberUuid(any(UUID.class)))
        .willReturn(Optional.empty());

    notificationPersistingService.send(member, payload("FEED_REACTION_ADDED"));

    then(notificationRepository).should().save(any());
  }

  @Test
  void sendAllowedWhenEventTypeHasNoMapping() {
    // 매핑 없는 이벤트 타입도 방해금지 시간 체크를 위해 설정 조회는 수행, 카테고리 차단 없이 발송
    given(notificationSettingsRepository.findByMemberUuid(any(UUID.class)))
        .willReturn(Optional.empty());

    notificationPersistingService.send(member, payload("UNKNOWN_EVENT_TYPE"));

    then(notificationRepository).should().save(any());
  }

  private NotificationPayload payload(String eventType) {
    return new NotificationPayload(eventType, "crew", "1", "dondok://feed", "테스트 알림", "테스트크루");
  }
}
