package com.oit.dondok.domain.crew.repository;

import com.oit.dondok.domain.crew.entity.Crew;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrewRepository extends JpaRepository<Crew, Long> {

  boolean existsByIdAndHostMemberUuid(Long id, UUID hostMemberUuid);

  @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
  @Query("SELECT c FROM Crew c WHERE c.id = :id")
  Optional<Crew> findByIdWithOptimisticLock(@Param("id") Long id);
}
