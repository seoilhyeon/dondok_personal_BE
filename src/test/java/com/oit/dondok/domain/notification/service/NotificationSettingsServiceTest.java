package com.oit.dondok.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.domain.notification.dto.request.NotificationSettingsRequest;
import com.oit.dondok.domain.notification.dto.response.NotificationSettingsResponse;
import com.oit.dondok.domain.notification.entity.NotificationCategory;
import com.oit.dondok.domain.notification.entity.NotificationSettings;
import com.oit.dondok.domain.notification.exception.NotificationErrorCode;
import com.oit.dondok.domain.notification.repository.NotificationSettingsRepository;
import com.oit.dondok.global.exception.CustomException;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationSettingsServiceTest {

  @Mock private NotificationSettingsRepository notificationSettingsRepository;
  @Mock private MemberRepository memberRepository;

  @InjectMocks private NotificationSettingsService notificationSettingsService;

  private static final UUID MEMBER_UUID = UUID.randomUUID();

  @Test
  void getSettingsReturnsDefaultsWhenNotExists() {
    given(notificationSettingsRepository.findByMemberUuid(MEMBER_UUID))
        .willReturn(Optional.empty());

    NotificationSettingsResponse response = notificationSettingsService.getSettings(MEMBER_UUID);

    assertThat(response.categories()).containsEntry(NotificationCategory.EMOJI_REACTION, true);
    assertThat(response.categories()).containsEntry(NotificationCategory.SETTLEMENT, true);
    assertThat(response.quietStartTime()).isNull();
    assertThat(response.quietEndTime()).isNull();
  }

  @Test
  void getSettingsReturnsPersistedSettings() {
    Member member = Member.create("test@example.com", null, "테스터");
    NotificationSettings settings = NotificationSettings.createDefault(member);
    settings.update(Map.of(NotificationCategory.EMOJI_REACTION, false), null, null);

    given(notificationSettingsRepository.findByMemberUuid(MEMBER_UUID))
        .willReturn(Optional.of(settings));

    NotificationSettingsResponse response = notificationSettingsService.getSettings(MEMBER_UUID);

    assertThat(response.categories()).containsEntry(NotificationCategory.EMOJI_REACTION, false);
    assertThat(response.categories()).containsEntry(NotificationCategory.SETTLEMENT, true);
  }

  @Test
  void saveSettingsCreatesNewRowWhenNotExists() {
    Member member = Member.create("test@example.com", null, "테스터");
    NotificationSettings created = NotificationSettings.createDefault(member);
    ReflectionTestUtils.setField(created, "createdAt", java.time.LocalDateTime.now());
    ReflectionTestUtils.setField(created, "updatedAt", java.time.LocalDateTime.now());

    given(notificationSettingsRepository.findByMemberUuid(MEMBER_UUID))
        .willReturn(Optional.empty());
    given(memberRepository.findByUuid(MEMBER_UUID)).willReturn(Optional.of(member));
    given(notificationSettingsRepository.save(any(NotificationSettings.class))).willReturn(created);

    NotificationSettingsRequest request =
        new NotificationSettingsRequest(
            Map.of(NotificationCategory.EMOJI_REACTION, false), omittedTime(), omittedTime());

    NotificationSettingsResponse response =
        notificationSettingsService.saveSettings(MEMBER_UUID, request);

    assertThat(response.categories()).isNotNull();
    then(notificationSettingsRepository).should().save(any(NotificationSettings.class));
  }

  @Test
  void saveSettingsUpdatesExistingRow() {
    Member member = Member.create("test@example.com", null, "테스터");
    NotificationSettings existing = NotificationSettings.createDefault(member);
    ReflectionTestUtils.setField(existing, "createdAt", java.time.LocalDateTime.now());
    ReflectionTestUtils.setField(existing, "updatedAt", java.time.LocalDateTime.now());

    given(notificationSettingsRepository.findByMemberUuid(MEMBER_UUID))
        .willReturn(Optional.of(existing));

    NotificationSettingsRequest request =
        new NotificationSettingsRequest(
            Map.of(NotificationCategory.SETTLEMENT, false), time("22:00"), time("07:00"));

    NotificationSettingsResponse response =
        notificationSettingsService.saveSettings(MEMBER_UUID, request);

    assertThat(response.categories()).containsEntry(NotificationCategory.SETTLEMENT, false);
    assertThat(response.quietStartTime()).isEqualTo("22:00");
    assertThat(response.quietEndTime()).isEqualTo("07:00");
    then(notificationSettingsRepository).should(never()).save(any());
  }

  @Test
  void saveSettingsPreservesExistingQuietHoursWhenTimesAreOmitted() {
    Member member = Member.create("test@example.com", null, "테스터");
    NotificationSettings existing = NotificationSettings.createDefault(member);
    existing.update(null, LocalTime.of(22, 0), LocalTime.of(7, 0));

    given(notificationSettingsRepository.findByMemberUuid(MEMBER_UUID))
        .willReturn(Optional.of(existing));

    NotificationSettingsRequest request =
        new NotificationSettingsRequest(
            Map.of(NotificationCategory.SETTLEMENT, false), omittedTime(), omittedTime());

    NotificationSettingsResponse response =
        notificationSettingsService.saveSettings(MEMBER_UUID, request);

    assertThat(response.categories()).containsEntry(NotificationCategory.SETTLEMENT, false);
    assertThat(response.quietStartTime()).isEqualTo("22:00");
    assertThat(response.quietEndTime()).isEqualTo("07:00");
  }

  @Test
  void saveSettingsClearsExistingQuietHoursWhenTimesAreExplicitNull() {
    Member member = Member.create("test@example.com", null, "테스터");
    NotificationSettings existing = NotificationSettings.createDefault(member);
    existing.update(null, LocalTime.of(22, 0), LocalTime.of(7, 0));

    given(notificationSettingsRepository.findByMemberUuid(MEMBER_UUID))
        .willReturn(Optional.of(existing));

    NotificationSettingsRequest request =
        new NotificationSettingsRequest(null, time(null), time(null));

    NotificationSettingsResponse response =
        notificationSettingsService.saveSettings(MEMBER_UUID, request);

    assertThat(response.quietStartTime()).isNull();
    assertThat(response.quietEndTime()).isNull();
  }

  @Test
  void saveSettingsPreservesExistingQuietEndWhenOnlyStartTimeProvided() {
    Member member = Member.create("test@example.com", null, "테스터");
    NotificationSettings existing = NotificationSettings.createDefault(member);
    existing.update(null, LocalTime.of(22, 0), LocalTime.of(7, 0));

    given(notificationSettingsRepository.findByMemberUuid(MEMBER_UUID))
        .willReturn(Optional.of(existing));

    NotificationSettingsRequest request =
        new NotificationSettingsRequest(null, time("23:00"), omittedTime());

    NotificationSettingsResponse response =
        notificationSettingsService.saveSettings(MEMBER_UUID, request);

    assertThat(response.quietStartTime()).isEqualTo("23:00");
    assertThat(response.quietEndTime()).isEqualTo("07:00");
  }

  @Test
  void saveSettingsThrowsWhenOnlyStartTimeProvided() {
    Member member = Member.create("test@example.com", null, "테스터");
    NotificationSettings existing = NotificationSettings.createDefault(member);
    given(notificationSettingsRepository.findByMemberUuid(MEMBER_UUID))
        .willReturn(Optional.of(existing));

    NotificationSettingsRequest request =
        new NotificationSettingsRequest(null, time("22:00"), omittedTime());

    assertThatThrownBy(() -> notificationSettingsService.saveSettings(MEMBER_UUID, request))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(NotificationErrorCode.INVALID_QUIET_HOURS);
  }

  @Test
  void saveSettingsThrowsWhenOnlyEndTimeProvided() {
    Member member = Member.create("test@example.com", null, "테스터");
    NotificationSettings existing = NotificationSettings.createDefault(member);
    given(notificationSettingsRepository.findByMemberUuid(MEMBER_UUID))
        .willReturn(Optional.of(existing));

    NotificationSettingsRequest request =
        new NotificationSettingsRequest(null, omittedTime(), time("07:00"));

    assertThatThrownBy(() -> notificationSettingsService.saveSettings(MEMBER_UUID, request))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(NotificationErrorCode.INVALID_QUIET_HOURS);
  }

  private static JsonNullable<String> time(String value) {
    return JsonNullable.of(value);
  }

  private static JsonNullable<String> omittedTime() {
    return JsonNullable.undefined();
  }
}
