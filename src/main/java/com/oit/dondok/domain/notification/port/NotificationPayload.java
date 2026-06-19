package com.oit.dondok.domain.notification.port;

public record NotificationPayload(
    String eventType,
    String resourceType,
    String resourceId,
    String deepLink,
    String displayText,
    String crewName) {}
