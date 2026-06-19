package com.oit.dondok.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.oit.dondok.domain.notification.dto.response.NotificationListResponse;
import com.oit.dondok.domain.notification.dto.response.ReadAllResponse;
import com.oit.dondok.domain.notification.exception.NotificationErrorCode;
import com.oit.dondok.domain.notification.repository.NotificationProjection;
import com.oit.dondok.domain.notification.repository.NotificationQueryRepository;
import com.oit.dondok.domain.notification.repository.NotificationRepository;
import com.oit.dondok.global.exception.CustomException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

  @Mock private NotificationQueryRepository notificationQueryRepository;
  @Mock private NotificationRepository notificationRepository;

  @InjectMocks private NotificationService notificationService;

  @Test
  void findNotificationsUsesDefaultLimitAndReturnsItems() {
    UUID memberUuid = UUID.randomUUID();
    LocalDateTime occurredAt = LocalDateTime.of(2026, 6, 10, 9, 0);
    UUID notifUuid = UUID.randomUUID();

    given(notificationQueryRepository.findByCursor(memberUuid, 21, null, null))
        .willReturn(
            List.of(
                new NotificationProjection(
                    1L,
                    notifUuid,
                    "MISSION_LOG_VERIFICATION_RESULT",
                    "mission_log",
                    "9001",
                    "dondok://crews/42/mission-logs/9001",
                    "인증 결과가 반영되었습니다.",
                    null,
                    true,
                    occurredAt,
                    null)));

    NotificationListResponse response =
        notificationService.findNotifications(memberUuid, null, null);

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().get(0).notificationId()).isEqualTo(notifUuid.toString());
    assertThat(response.items().get(0).eventType()).isEqualTo("MISSION_LOG_VERIFICATION_RESULT");
    assertThat(response.items().get(0).resourceType()).isEqualTo("mission_log");
    assertThat(response.items().get(0).resourceId()).isEqualTo("9001");
    assertThat(response.items().get(0).deepLink()).isEqualTo("dondok://crews/42/mission-logs/9001");
    assertThat(response.items().get(0).displayText()).isEqualTo("인증 결과가 반영되었습니다.");
    assertThat(response.items().get(0).requiresRefetch()).isTrue();
    assertThat(response.items().get(0).occurredAt())
        .isEqualTo(occurredAt.atZone(SEOUL_ZONE).toOffsetDateTime());
    assertThat(response.items().get(0).readAt()).isNull();
    assertThat(response.nextCursor()).isNull();
  }

  @Test
  void findNotificationsReturnsNextCursorWhenPageOverflowsLimit() {
    UUID memberUuid = UUID.randomUUID();
    LocalDateTime newer = LocalDateTime.of(2026, 6, 10, 10, 0);
    LocalDateTime older = LocalDateTime.of(2026, 6, 10, 9, 0);
    LocalDateTime oldest = LocalDateTime.of(2026, 6, 10, 8, 0);

    List<NotificationProjection> rows =
        List.of(
            projection(3L, UUID.randomUUID(), "CREW_ACTIVATED", newer, null),
            projection(2L, UUID.randomUUID(), "CREW_APPLICATION_APPROVED", older, null),
            projection(1L, UUID.randomUUID(), "SETTLEMENT_COMPLETED", oldest, null));

    given(notificationQueryRepository.findByCursor(memberUuid, 3, null, null)).willReturn(rows);

    NotificationListResponse response = notificationService.findNotifications(memberUuid, 2, null);

    assertThat(response.items()).hasSize(2);
    assertThat(response.nextCursor()).isNotNull();
    assertThat(decodeCursor(response.nextCursor())).isEqualTo("v1|2026-06-10T09:00+09:00|2");
  }

  @Test
  void findNotificationsSupportsCursorPagination() {
    UUID memberUuid = UUID.randomUUID();
    OffsetDateTime cursorTime =
        LocalDateTime.of(2026, 6, 10, 10, 0).atZone(SEOUL_ZONE).toOffsetDateTime();
    String cursor = encodeCursor(cursorTime, 5L);
    LocalDateTime cursorOccurredAt = cursorTime.toLocalDateTime();

    given(notificationQueryRepository.findByCursor(memberUuid, 6, cursorOccurredAt, 5L))
        .willReturn(
            List.of(
                projection(
                    4L,
                    UUID.randomUUID(),
                    "CREW_ACTIVATED",
                    cursorOccurredAt.minusHours(1),
                    null)));

    NotificationListResponse response =
        notificationService.findNotifications(memberUuid, 5, cursor);

    assertThat(response.items()).hasSize(1);
    assertThat(response.nextCursor()).isNull();
    then(notificationQueryRepository).should().findByCursor(memberUuid, 6, cursorOccurredAt, 5L);
  }

  @Test
  void findNotificationsIncludesReadAtWhenNotificationIsRead() {
    UUID memberUuid = UUID.randomUUID();
    LocalDateTime occurredAt = LocalDateTime.of(2026, 6, 10, 9, 0);
    LocalDateTime readAt = LocalDateTime.of(2026, 6, 10, 10, 0);

    given(notificationQueryRepository.findByCursor(memberUuid, 21, null, null))
        .willReturn(
            List.of(projection(1L, UUID.randomUUID(), "CREW_ACTIVATED", occurredAt, readAt)));

    NotificationListResponse response =
        notificationService.findNotifications(memberUuid, null, null);

    assertThat(response.items().get(0).readAt())
        .isEqualTo(readAt.atZone(SEOUL_ZONE).toOffsetDateTime());
  }

  @Test
  void findNotificationsThrowsWhenLimitIsOutOfRange() {
    UUID memberUuid = UUID.randomUUID();

    assertThatThrownBy(() -> notificationService.findNotifications(memberUuid, 0, null))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(NotificationErrorCode.INVALID_LIMIT);

    assertThatThrownBy(() -> notificationService.findNotifications(memberUuid, 101, null))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(NotificationErrorCode.INVALID_LIMIT);
  }

  @Test
  void findNotificationsThrowsWhenCursorIsInvalidFormat() {
    UUID memberUuid = UUID.randomUUID();

    assertThatThrownBy(() -> notificationService.findNotifications(memberUuid, null, "invalid"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(NotificationErrorCode.INVALID_CURSOR);
  }

  @Test
  void findNotificationsThrowsWhenCursorHasInvalidPadding() {
    UUID memberUuid = UUID.randomUUID();

    assertThatThrownBy(() -> notificationService.findNotifications(memberUuid, null, "a"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(NotificationErrorCode.INVALID_CURSOR);
  }

  @Test
  void markAllAsReadReturnsUpdatedCount() {
    UUID memberUuid = UUID.randomUUID();
    given(notificationRepository.markAllAsRead(eq(memberUuid))).willReturn(3);

    ReadAllResponse response = notificationService.markAllAsRead(memberUuid);

    assertThat(response.updatedCount()).isEqualTo(3);
  }

  @Test
  void markAllAsReadReturnsZeroWhenNoUnreadNotifications() {
    UUID memberUuid = UUID.randomUUID();
    given(notificationRepository.markAllAsRead(eq(memberUuid))).willReturn(0);

    ReadAllResponse response = notificationService.markAllAsRead(memberUuid);

    assertThat(response.updatedCount()).isEqualTo(0);
  }

  @Test
  void findNotificationsTreatsBlankCursorAsNoCursor() {
    UUID memberUuid = UUID.randomUUID();
    given(notificationQueryRepository.findByCursor(memberUuid, 21, null, null))
        .willReturn(List.of());

    NotificationListResponse response =
        notificationService.findNotifications(memberUuid, null, "   ");

    assertThat(response.items()).isEmpty();
    assertThat(response.nextCursor()).isNull();
    then(notificationQueryRepository).should().findByCursor(memberUuid, 21, null, null);
  }

  @Test
  void findNotificationsThrowsWhenCursorVersionIsInvalid() {
    UUID memberUuid = UUID.randomUUID();
    String cursor = encodeRawCursor("v2|2026-06-10T09:00:00+09:00|1");

    assertThatThrownBy(() -> notificationService.findNotifications(memberUuid, null, cursor))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(NotificationErrorCode.INVALID_CURSOR);
  }

  @Test
  void findNotificationsThrowsWhenCursorIdIsNotNumeric() {
    UUID memberUuid = UUID.randomUUID();
    String cursor = encodeRawCursor("v1|2026-06-10T09:00:00+09:00|abc");

    assertThatThrownBy(() -> notificationService.findNotifications(memberUuid, null, cursor))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(NotificationErrorCode.INVALID_CURSOR);
  }

  @Test
  void findNotificationsThrowsWhenCursorDateIsUnparseable() {
    UUID memberUuid = UUID.randomUUID();
    String cursor = encodeRawCursor("v1|not-a-date|1");

    assertThatThrownBy(() -> notificationService.findNotifications(memberUuid, null, cursor))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(NotificationErrorCode.INVALID_CURSOR);
  }

  @Test
  void findNotificationsAcceptsMinimumLimit() {
    UUID memberUuid = UUID.randomUUID();
    given(notificationQueryRepository.findByCursor(memberUuid, 2, null, null))
        .willReturn(List.of());

    assertThatNoException()
        .isThrownBy(() -> notificationService.findNotifications(memberUuid, 1, null));
  }

  @Test
  void findNotificationsAcceptsMaximumLimit() {
    UUID memberUuid = UUID.randomUUID();
    given(notificationQueryRepository.findByCursor(memberUuid, 101, null, null))
        .willReturn(List.of());

    assertThatNoException()
        .isThrownBy(() -> notificationService.findNotifications(memberUuid, 100, null));
  }

  private static NotificationProjection projection(
      Long id, UUID uuid, String eventType, LocalDateTime occurredAt, LocalDateTime readAt) {
    return new NotificationProjection(
        id,
        uuid,
        eventType,
        "crew",
        "42",
        "dondok://crews/42",
        "알림입니다.",
        null,
        true,
        occurredAt,
        readAt);
  }

  private static String encodeCursor(OffsetDateTime occurredAt, Long id) {
    String payload = "v1|" + occurredAt + "|" + id;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }

  private static String encodeRawCursor(String payload) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }

  private static String decodeCursor(String cursor) {
    int remainder = cursor.length() % 4;
    String padded = remainder == 0 ? cursor : cursor + "=".repeat(4 - remainder);
    return new String(Base64.getUrlDecoder().decode(padded), StandardCharsets.UTF_8);
  }
}
