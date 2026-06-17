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
      @UniqueConstraint(name = "uk_member_nickname", columnNames = "nickname"),
      @UniqueConstraint(
          name = "uk_member_oauth_provider_id",
          columnNames = {"oauth_provider", "oauth_provider_id"})
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
  @Column(name = "oauth_provider", length = 20)
  private OAuthProvider oauthProvider;

  @Column(name = "oauth_provider_id", length = 100)
  private String oauthProviderId;

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

  /** Google OAuth 회원을 생성한다. */
  public static Member createOAuthMember(
      String email, String nickname, OAuthProvider oauthProvider, String oauthProviderId) {
    Member member = new Member();
    member.uuid = UUID.randomUUID();
    member.email = email;
    member.passwordHash = null;
    member.nickname = nickname;
    member.oauthProvider = oauthProvider;
    member.oauthProviderId = oauthProviderId;
    member.status = MemberStatus.ACTIVE;
    return member;
  }

  /** 기존 회원에 OAuth 계정을 연결한다. */
  public void connectOAuth(OAuthProvider oauthProvider, String oauthProviderId) {
    this.oauthProvider = oauthProvider;
    this.oauthProviderId = oauthProviderId;
  }

  /** 다른 OAuth 계정이 이미 연결되어 있는지 확인한다. */
  public boolean hasDifferentOAuthAccount(OAuthProvider oauthProvider, String oauthProviderId) {
    if (this.oauthProvider == null && this.oauthProviderId == null) {
      return false;
    }
    if (this.oauthProvider == null || this.oauthProviderId == null) {
      return true;
    }
    return this.oauthProvider != oauthProvider || !this.oauthProviderId.equals(oauthProviderId);
  }

  public void updateProfile(String nickname, String profileImageS3Key, String statusMessage) {
    this.nickname = nickname;
    this.profileImageS3Key = profileImageS3Key;
    this.statusMessage = statusMessage;
  }
}
