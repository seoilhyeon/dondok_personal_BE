package com.oit.dondok.domain.notification.repository;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.notification.entity.NotificationDevice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeviceRepository extends JpaRepository<NotificationDevice, Long> {

  List<NotificationDevice> findByMemberAndEnabledTrue(Member member);

  Optional<NotificationDevice> findByMemberAndDeviceId(Member member, String deviceId);
}
