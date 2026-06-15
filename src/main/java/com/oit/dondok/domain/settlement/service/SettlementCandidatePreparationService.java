package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.repository.MissionRuleRepository;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementRuleContextSnapshot;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettlementCandidatePreparationService {

  private final CrewRepository crewRepository;
  private final MissionRuleRepository missionRuleRepository;
  private final SettlementRepository settlementRepository;
  private final SettlementEligibilityPolicy settlementEligibilityPolicy;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Optional<Long> prepareCompletedCrewSettlementCandidate(
      Long crewId, String batchRunKey, LocalDateTime now) {
    Crew crew =
        crewRepository
            .findByIdWithOptimisticLock(crewId)
            .orElseThrow(() -> new IllegalStateException("크루를 찾을 수 없습니다. crewId=" + crewId));
    MissionRule missionRule =
        missionRuleRepository
            .findByCrewId(crewId)
            .orElseThrow(() -> new IllegalStateException("미션 규칙을 찾을 수 없습니다. crewId=" + crewId));

    if (!settlementEligibilityPolicy.isCompletedCrewEligible(crew, missionRule, now)) {
      return Optional.empty();
    }
    if (crew.getStatus() == CrewStatus.ACTIVE) {
      crew.close();
    }

    Settlement settlement =
        settlementRepository
            .findByCrewId(crewId)
            .orElseGet(
                () ->
                    settlementRepository.save(
                        Settlement.createPending(
                            crew,
                            batchRunKey,
                            now,
                            SettlementRuleContextSnapshot.from(
                                missionRule.getDailySettlementType(),
                                missionRule.getFrequencyType()))));
    return Optional.of(settlement.getId());
  }
}
