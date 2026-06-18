package com.oit.dondok.domain.notification.repository;

import com.oit.dondok.domain.notification.entity.Notification;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

  @Modifying(clearAutomatically = true)
  @Query(
      """
      UPDATE Notification n SET n.readAt = CURRENT_TIMESTAMP
      WHERE n.member.uuid = :memberUuid AND n.readAt IS NULL
      """)
  int markAllAsRead(@Param("memberUuid") UUID memberUuid);
}
