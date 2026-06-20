package com.oit.dondok.domain.notification.repository;

import com.oit.dondok.domain.notification.entity.NotificationSettings;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, Long> {

  @Query("SELECT s FROM NotificationSettings s WHERE s.member.uuid = :memberUuid")
  Optional<NotificationSettings> findByMemberUuid(@Param("memberUuid") UUID memberUuid);
}
