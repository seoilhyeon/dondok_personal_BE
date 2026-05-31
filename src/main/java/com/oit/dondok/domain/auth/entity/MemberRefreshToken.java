package com.oit.dondok.domain.auth.entity;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.global.entity.CreatedTimeEntity;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** TODO: Redis-backed refresh session 이관 전까지 사용하는 임시 저장소입니다. 이관 시 해당 엔티티 및 관련 JPA 로직은 제거될 예정입니다. */
@Getter
@Entity
@Table(
    name = "member_refresh_token",
    indexes =
        @Index(
            name = "idx_member_refresh_token_member_expires",
            columnList = "member_id, expires_at"),
    uniqueConstraints =
        @UniqueConstraint(name = "uk_member_refresh_token_hash", columnNames = "token_hash"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberRefreshToken extends CreatedTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Column(name = "token_hash", nullable = false, columnDefinition = "char(64)")
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "revoked_at")
  private LocalDateTime revokedAt;

  /** 원문 token 대신 해시된 값으로 저장할 refresh token record를 생성한다. */
  public static MemberRefreshToken create(
      Member member, String tokenHash, LocalDateTime expiresAt) {
    if (member == null) {
      throw new IllegalArgumentException("member must not be null");
    }
    if (tokenHash == null || tokenHash.isBlank()) {
      throw new IllegalArgumentException("tokenHash must not be null or blank");
    }
    if (expiresAt == null) {
      throw new IllegalArgumentException("expiresAt must not be null");
    }
    if (!expiresAt.isAfter(LocalDateTime.now())) {
      throw new IllegalArgumentException("expiresAt must be a future time");
    }
    MemberRefreshToken refreshToken = new MemberRefreshToken();
    refreshToken.member = member;
    refreshToken.tokenHash = tokenHash;
    refreshToken.expiresAt = expiresAt;
    return refreshToken;
  }
}
