package com.oit.dondok.domain.crew.repository;

import com.oit.dondok.domain.crew.entity.CrewParticipant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrewParticipantRepository extends JpaRepository<CrewParticipant, Long> {

  Optional<CrewParticipant> findByCrewIdAndMemberUuid(Long crewId, UUID memberUuid);
}
