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

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<MemberRefreshToken> findByTokenHash(String tokenHash);

  @Modifying
  @Query(
      """
      update MemberRefreshToken token
      set token.tokenHash = :tokenHash,
          token.expiresAt = :expiresAt,
          token.revokedAt = null
      where token.id = :id
      """)
  int rotateById(
      @Param("id") Long id,
      @Param("tokenHash") String tokenHash,
      @Param("expiresAt") LocalDateTime expiresAt);
}
