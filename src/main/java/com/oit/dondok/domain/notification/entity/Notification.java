package com.oit.dondok.domain.notification.entity;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.notification.port.NotificationPayload;
import com.oit.dondok.global.entity.AuditableTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "notification",
    indexes = {
      @Index(name = "idx_notification_member_occurred", columnList = "member_id, occurred_at, id"),
      @Index(name = "idx_notification_member_read", columnList = "member_id, read_at")
    },
    uniqueConstraints = @UniqueConstraint(name = "uk_notification_uuid", columnNames = "uuid"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends AuditableTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "uuid", nullable = false)
  private UUID uuid;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Column(name = "event_type", nullable = false, length = 80)
  private String eventType; // TODO: 도메인 담당자가 enum 필요시 도입

  @Column(name = "resource_type", nullable = false, length = 50)
  private String resourceType; // TODO: 도메인 담당자가 enum 필요시 도입

  @Column(name = "resource_id", nullable = false, length = 100)
  private String resourceId;

  @Column(name = "deep_link", nullable = false, length = 255)
  private String deepLink;

  @Column(name = "display_text", nullable = false, length = 500)
  private String displayText;

  @Column(name = "crew_name", length = 100)
  private String crewName;

  @Column(name = "requires_refetch", nullable = false)
  private Boolean requiresRefetch;

  @Column(name = "occurred_at", nullable = false)
  private LocalDateTime occurredAt;

  @Column(name = "read_at")
  private LocalDateTime readAt;

  public void markAsRead() {
    if (this.readAt == null) {
      this.readAt = LocalDateTime.now();
    }
  }

  public static Notification create(Member member, NotificationPayload payload) {
    Notification n = new Notification();
    n.uuid = UUID.randomUUID();
    n.member = member;
    n.eventType = payload.eventType();
    n.resourceType = payload.resourceType();
    n.resourceId = payload.resourceId();
    n.deepLink = payload.deepLink();
    n.displayText = payload.displayText();
    n.crewName = payload.crewName();
    n.requiresRefetch = true;
    n.occurredAt = LocalDateTime.now();
    return n;
  }
}
