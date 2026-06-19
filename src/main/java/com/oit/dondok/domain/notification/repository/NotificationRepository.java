package com.oit.dondok.domain.notification.repository;

import com.oit.dondok.domain.notification.entity.Notification;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

  @Query("SELECT n FROM Notification n WHERE n.uuid = :uuid AND n.member.uuid = :memberUuid")
  Optional<Notification> findByUuidAndMemberUuid(
      @Param("uuid") UUID uuid, @Param("memberUuid") UUID memberUuid);

  @Query(
      "SELECT COUNT(n) FROM Notification n WHERE n.member.uuid = :memberUuid AND n.readAt IS NULL")
  long countUnread(@Param("memberUuid") UUID memberUuid);

  @Modifying(clearAutomatically = true)
  @Query(
      """
      UPDATE Notification n SET n.readAt = CURRENT_TIMESTAMP
      WHERE n.member.uuid = :memberUuid AND n.readAt IS NULL
      """)
  int markAllAsRead(@Param("memberUuid") UUID memberUuid);
}
