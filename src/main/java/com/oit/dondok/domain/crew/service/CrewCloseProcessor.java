package com.oit.dondok.domain.crew.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import com.oit.dondok.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CrewCloseProcessor {

  private final CrewRepository crewRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void closeOne(Long crewId) {
    Crew crew =
        crewRepository
            .findById(crewId)
            .orElseThrow(() -> new CustomException(CrewErrorCode.CREW_NOT_FOUND));

    if (crew.getStatus() != CrewStatus.ACTIVE) {
      log.info("[배치] 크루 CLOSED 스킵 (ACTIVE 아님): crewId={}, status={}", crewId, crew.getStatus());
      return;
    }

    crew.close();
    log.info("[배치] 크루 CLOSED 전환: crewId={}", crewId);
  }
}
