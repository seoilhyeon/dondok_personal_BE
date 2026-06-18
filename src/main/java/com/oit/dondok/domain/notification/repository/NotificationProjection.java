package com.oit.dondok.domain.notification.repository;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationProjection(
    Long id,
    UUID uuid,
    String eventType,
    String resourceType,
    String resourceId,
    String deepLink,
    String displayText,
    Boolean requiresRefetch,
    LocalDateTime occurredAt,
    LocalDateTime readAt) {}
