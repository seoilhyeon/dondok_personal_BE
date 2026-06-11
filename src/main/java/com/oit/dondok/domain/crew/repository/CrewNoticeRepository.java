package com.oit.dondok.domain.crew.repository;

import com.oit.dondok.domain.crew.entity.CrewNotice;
import com.oit.dondok.domain.crew.entity.CrewNoticeStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrewNoticeRepository extends JpaRepository<CrewNotice, Long> {

  @EntityGraph(attributePaths = {"authorMember"})
  List<CrewNotice> findByCrewIdAndStatusAndIdLessThanOrderByIdDesc(
      Long crewId, CrewNoticeStatus status, Long id, Pageable pageable);
}
