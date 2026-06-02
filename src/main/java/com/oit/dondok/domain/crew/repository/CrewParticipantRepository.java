package com.oit.dondok.domain.crew.repository;

import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrewParticipantRepository extends JpaRepository<CrewParticipant, Long> {

  Optional<CrewParticipant> findByCrewIdAndMemberUuid(Long crewId, UUID memberUuid);

  long countByCrewIdAndStatusIn(Long crewId, List<CrewParticipantStatus> statuses);
}
