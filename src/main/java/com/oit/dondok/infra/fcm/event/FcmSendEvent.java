package com.oit.dondok.infra.fcm.event;

import com.oit.dondok.domain.notification.port.NotificationPayload;

public record FcmSendEvent(String fcmToken, NotificationPayload payload) {}
