package com.oit.dondok.domain.notification.repository;

import static com.oit.dondok.domain.notification.entity.QNotificationDevice.notificationDevice;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationDeviceCommandRepository {

  private final JPAQueryFactory queryFactory;

  public int disableByFcmToken(String fcmToken) {
    return (int)
        queryFactory
            .update(notificationDevice)
            .set(notificationDevice.enabled, false)
            .where(notificationDevice.fcmToken.eq(fcmToken), notificationDevice.enabled.isTrue())
            .execute();
  }
}
