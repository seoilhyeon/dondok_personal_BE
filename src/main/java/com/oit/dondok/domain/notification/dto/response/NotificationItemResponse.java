package com.oit.dondok.domain.notification.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record NotificationItemResponse(
    String notificationId,
    String eventType,
    String resourceType,
    String resourceId,
    String deepLink,
    OffsetDateTime occurredAt,
    String displayText,
    Boolean requiresRefetch,
    OffsetDateTime readAt) {}
