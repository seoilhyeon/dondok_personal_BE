package com.oit.dondok.domain.auth.repository;

import com.oit.dondok.domain.auth.entity.MemberRefreshToken;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRefreshTokenRepository extends JpaRepository<MemberRefreshToken, Long> {

  // Lock the stored token row so concurrent rotations serialize on the same hash.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<MemberRefreshToken> findByTokenHash(String tokenHash);

  // Conditional update keeps refresh tokens single-use after rotation.
  @Modifying
  @Query(
      """
      update MemberRefreshToken token
      set token.tokenHash = :newTokenHash,
          token.expiresAt = :expiresAt,
          token.revokedAt = null
      where token.id = :id
        and token.tokenHash = :oldTokenHash
      """)
  int rotateById(
      @Param("id") Long id,
      @Param("oldTokenHash") String oldTokenHash,
      @Param("newTokenHash") String newTokenHash,
      @Param("expiresAt") LocalDateTime expiresAt);
}
