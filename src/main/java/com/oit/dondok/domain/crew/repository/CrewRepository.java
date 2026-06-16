package com.oit.dondok.domain.crew.repository;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrewRepository extends JpaRepository<Crew, Long> {

  boolean existsByIdAndHostMemberUuid(Long id, UUID hostMemberUuid);

  long countByHostMemberUuidAndStatusIn(UUID hostMemberUuid, List<CrewStatus> statuses);

  @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
  @Query("SELECT c FROM Crew c WHERE c.id = :id")
  Optional<Crew> findByIdWithOptimisticLock(@Param("id") Long id);

  List<Crew> findByStatusAndStartAtBefore(CrewStatus status, LocalDateTime now);

  List<Crew> findByStatusAndEndAtLessThanEqual(CrewStatus status, LocalDateTime now);

  @Query(
      """
      select c
        from Crew c
        left join Settlement s on s.crew = c
       where c.status = com.oit.dondok.domain.crew.entity.CrewStatus.CLOSED
         and s.id is null
      """)
  List<Crew> findClosedWithoutSettlement();
}
