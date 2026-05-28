package com.oit.dondok.domain.notification.entity;

import com.oit.dondok.domain.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "notification_device",
    indexes =
        @Index(name = "idx_notification_device_member_enabled", columnList = "member_id, enabled"),
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_notification_device_member_device",
            columnNames = {"member_id", "device_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationDevice {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Column(name = "device_id", nullable = false, length = 100)
  private String deviceId;

  @Enumerated(EnumType.STRING)
  @Column(name = "platform", nullable = false, length = 20)
  private NotificationPlatform platform;

  @Column(name = "fcm_token", nullable = false, length = 512)
  private String fcmToken;

  @Column(name = "app_version", length = 50)
  private String appVersion;

  @Column(name = "enabled", nullable = false)
  private Boolean enabled;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
