package com.oit.dondok.domain.point.repository;

import com.oit.dondok.domain.point.entity.PointAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointAccountRepository extends JpaRepository<PointAccount, Long> {

  Optional<PointAccount> findByMemberId(Long memberId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select account from PointAccount account where account.member.id = :memberId")
  Optional<PointAccount> findByMemberIdForUpdate(@Param("memberId") Long memberId);

  @Query("select account from PointAccount account where account.member.uuid = :memberUuid")
  Optional<PointAccount> findByMemberUuid(@Param("memberUuid") UUID memberUuid);
}
