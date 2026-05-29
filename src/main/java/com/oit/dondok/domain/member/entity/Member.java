package com.oit.dondok.domain.member.entity;

import com.oit.dondok.global.entity.AuditableTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "member",
    indexes = @Index(name = "idx_member_status", columnList = "status"),
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_member_uuid", columnNames = "uuid"),
      @UniqueConstraint(name = "uk_member_email", columnNames = "email"),
      @UniqueConstraint(name = "uk_member_nickname", columnNames = "nickname")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends AuditableTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "uuid", nullable = false)
  private UUID uuid;

  @Column(name = "email", nullable = false)
  private String email;

  @Column(name = "password_hash")
  private String passwordHash;

  @Column(name = "nickname", nullable = false, length = 50)
  private String nickname;

  @Column(name = "profile_image_s3_key", length = 255)
  private String profileImageS3Key;

  @Column(name = "status_message", length = 100)
  private String statusMessage;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private MemberStatus status;

  public static Member create(String email, String passwordHash, String nickname) {
    Member member = new Member();
    member.uuid = UUID.randomUUID();
    member.email = email;
    member.passwordHash = passwordHash;
    member.nickname = nickname;
    member.status = MemberStatus.ACTIVE;
    return member;
  }
}
