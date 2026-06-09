package com.oit.dondok.domain.crew.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrewActivationBatchService {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

  private final CrewRepository crewRepository;

  @Transactional(readOnly = true)
  public List<Crew> findRecruitingCrewsToActivate() {
    return crewRepository.findByStatusAndStartAtBefore(
        CrewStatus.RECRUITING, LocalDateTime.now(SEOUL_ZONE));
  }

  @Transactional
  public void activateCrews() {
    LocalDateTime now = LocalDateTime.now(SEOUL_ZONE);
    List<Crew> crews = crewRepository.findByStatusAndStartAtBefore(CrewStatus.RECRUITING, now);
    crews.forEach(crew -> crew.activate(now));
  }
}
