package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.repository.MissionRuleRepository;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementFailureCode;
import com.oit.dondok.domain.settlement.entity.SettlementRuleContextSnapshot;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
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
      Long crewId, DailySettlementType dailySettlementType, String batchRunKey, LocalDateTime now) {
    Crew crew;
    MissionRule missionRule;
    try {
      crew =
          crewRepository
              .findByIdWithOptimisticLock(crewId)
              .orElseThrow(
                  () ->
                      new SettlementBatchRunFailure(
                          SettlementFailureCode.INPUT_LOAD_FAILED,
                          "요청한 크루를 찾을 수 없습니다. crewId=" + crewId));
      missionRule =
          missionRuleRepository
              .findByCrewId(crewId)
              .orElseThrow(
                  () ->
                      new SettlementBatchRunFailure(
                          SettlementFailureCode.INPUT_LOAD_FAILED,
                          "미션 룰 정보를 찾을 수 없습니다. crewId=" + crewId));
    } catch (SettlementBatchRunFailure failure) {
      throw failure;
    } catch (RuntimeException exception) {
      throw new SettlementBatchRunFailure(
          SettlementFailureCode.UNKNOWN, "크루 정산 후보 준비 중 오류가 발생했습니다. crewId=" + crewId, exception);
    }

    if (dailySettlementType == null) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
    if (missionRule.getDailySettlementType() != dailySettlementType) {
      return Optional.empty();
    }
    if (!settlementEligibilityPolicy.isCompletedCrewEligible(crew, missionRule, now)) {
      return Optional.empty();
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
