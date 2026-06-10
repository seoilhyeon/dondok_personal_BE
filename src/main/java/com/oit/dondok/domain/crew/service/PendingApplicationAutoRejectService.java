package com.oit.dondok.domain.crew.service;

import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.port.CrewPointPort;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
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
public class PendingApplicationAutoRejectService {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

  private final CrewParticipantRepository crewParticipantRepository;
  private final CrewPointPort crewPointPort;

  @Transactional
  public void rejectExpiredApplications() {
    LocalDateTime now = LocalDateTime.now(SEOUL_ZONE);
    log.info("[배치] PENDING 신청 자동 만료 시작: {}", now);
    try {
      List<CrewParticipant> targets =
          crewParticipantRepository
              .findByStatusAndCrewStatusAndCrewRecruitmentDeadlineLessThanEqual(
                  CrewParticipantStatus.PENDING, CrewStatus.RECRUITING, now);
      log.info("[배치] 자동 만료 대상 신청 수: {}", targets.size());
      int count = 0;
      for (CrewParticipant participant : targets) {
        try {
          participant.expire(now);
          crewParticipantRepository.saveAndFlush(participant);
          crewPointPort.releaseExpiredReserve(participant);
          count++;
        } catch (Exception e) {
          log.error("[배치] 신청 만료 처리 실패 - participantId: {}", participant.getId(), e);
          throw e;
        }
      }
      log.info("[배치] PENDING 신청 자동 만료 완료: {}건", count);
    } catch (Exception e) {
      log.error("[배치] PENDING 신청 자동 만료 중 예외 발생", e);
      throw e;
    }
  }
}
