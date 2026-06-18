package com.oit.dondok.domain.notification.service;

import com.oit.dondok.domain.notification.dto.response.NotificationItemResponse;
import com.oit.dondok.domain.notification.dto.response.NotificationListResponse;
import com.oit.dondok.domain.notification.dto.response.ReadAllResponse;
import com.oit.dondok.domain.notification.exception.NotificationErrorCode;
import com.oit.dondok.domain.notification.repository.NotificationProjection;
import com.oit.dondok.domain.notification.repository.NotificationQueryRepository;
import com.oit.dondok.domain.notification.repository.NotificationRepository;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.util.SeoulDateTimeUtils;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 100;
  private static final String CURSOR_VERSION = "v1";
  private static final String CURSOR_DELIMITER = "|";

  private final NotificationQueryRepository notificationQueryRepository;
  private final NotificationRepository notificationRepository;

  @Transactional(readOnly = true)
  public NotificationListResponse findNotifications(
      UUID memberUuid, Integer limitInput, String cursor) {
    int effectiveLimit = resolveLimit(limitInput);
    Cursor cursorState = parseCursor(cursor);

    List<NotificationProjection> rows =
        notificationQueryRepository.findByCursor(
            memberUuid, effectiveLimit + 1, cursorState.occurredAt(), cursorState.id());

    boolean hasNext = rows.size() > effectiveLimit;
    List<NotificationProjection> pageItems = hasNext ? rows.subList(0, effectiveLimit) : rows;

    List<NotificationItemResponse> items =
        pageItems.stream()
            .map(
                p ->
                    new NotificationItemResponse(
                        p.uuid().toString(),
                        p.eventType(),
                        p.resourceType(),
                        p.resourceId(),
                        p.deepLink(),
                        SeoulDateTimeUtils.toSeoulOffset(p.occurredAt()),
                        p.displayText(),
                        p.requiresRefetch(),
                        SeoulDateTimeUtils.toSeoulOffset(p.readAt())))
            .toList();

    String nextCursor =
        hasNext
            ? encodeCursor(
                pageItems.get(pageItems.size() - 1).occurredAt(),
                pageItems.get(pageItems.size() - 1).id())
            : null;

    return new NotificationListResponse(items, nextCursor);
  }

  @Transactional
  public ReadAllResponse markAllAsRead(UUID memberUuid) {
    int updatedCount = notificationRepository.markAllAsRead(memberUuid, LocalDateTime.now());
    return new ReadAllResponse(updatedCount);
  }

  private int resolveLimit(Integer limitInput) {
    int limit = limitInput == null ? DEFAULT_LIMIT : limitInput;
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new CustomException(NotificationErrorCode.INVALID_LIMIT);
    }
    return limit;
  }

  private Cursor parseCursor(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return new Cursor(null, null);
    }
    try {
      String decoded =
          new String(
              Base64.getUrlDecoder().decode(restoreBase64Padding(cursor.trim())),
              StandardCharsets.UTF_8);
      String[] parts = decoded.split("\\|", -1);
      if (parts.length != 3 || !CURSOR_VERSION.equals(parts[0])) {
        throw new CustomException(NotificationErrorCode.INVALID_CURSOR);
      }
      OffsetDateTime occurredAt = OffsetDateTime.parse(parts[1]);
      Long id = Long.parseLong(parts[2]);
      LocalDateTime occurredAtInSeoul = occurredAt.atZoneSameInstant(SEOUL_ZONE).toLocalDateTime();
      return new Cursor(occurredAtInSeoul, id);
    } catch (DateTimeParseException | IllegalArgumentException e) {
      throw new CustomException(NotificationErrorCode.INVALID_CURSOR);
    }
  }

  private String encodeCursor(LocalDateTime occurredAt, Long id) {
    String payload =
        String.join(
            CURSOR_DELIMITER,
            CURSOR_VERSION,
            SeoulDateTimeUtils.toSeoulOffset(occurredAt).toString(),
            id.toString());
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }

  private String restoreBase64Padding(String cursor) {
    int remainder = cursor.length() % 4;
    if (remainder == 0) {
      return cursor;
    }
    if (remainder == 1) {
      throw new CustomException(NotificationErrorCode.INVALID_CURSOR);
    }
    return cursor + "=".repeat(4 - remainder);
  }

  private record Cursor(LocalDateTime occurredAt, Long id) {}
}
