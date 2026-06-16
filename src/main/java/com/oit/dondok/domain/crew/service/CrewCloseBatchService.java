package com.oit.dondok.domain.crew.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrewCloseBatchService {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

  private final CrewRepository crewRepository;
  private final CrewCloseProcessor crewCloseProcessor;

  @Transactional
  public void closeCrews() {
    LocalDateTime startTime = LocalDateTime.now(SEOUL_ZONE);
    log.info("[배치] 크루 종료 처리 시작: {}", startTime);

    List<Crew> crews =
        crewRepository.findByStatusAndEndAtLessThanEqual(CrewStatus.ACTIVE, startTime);
    log.info("[배치] 종료 대상 크루 수: {}", crews.size());

    int processedCount = 0;
    for (Crew crew : crews) {
      try {
        crewCloseProcessor.closeOne(crew.getId());
        processedCount++;
      } catch (Exception e) {
        log.error("[배치] 크루 처리 실패 - crewId: {}", crew.getId(), e);
      }
    }

    long elapsedMs = Duration.between(startTime, LocalDateTime.now(SEOUL_ZONE)).toMillis();
    log.info("[배치] 크루 종료 처리 완료: {}건 처리, 소요시간: {}ms", processedCount, elapsedMs);
  }
}
