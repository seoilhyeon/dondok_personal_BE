package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.exception.SettlementErrorCode;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SettlementQueryGuard {
  private final CrewRepository crewRepository;
  private final CrewParticipantRepository crewParticipantRepository;
  private final SettlementRepository settlementRepository;

  public void validateSummaryQueryInput(Long crewId, UUID memberUuid) {
    if (crewId == null || crewId <= 0L || memberUuid == null) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
  }

  public @NonNull Optional<Settlement> authorizeSummaryQuery(Long crewId, UUID memberUuid) {
    validateSummaryQueryInput(crewId, memberUuid);

    if (!crewRepository.existsById(crewId)) {
      throw new CustomException(CrewErrorCode.CREW_NOT_FOUND);
    }

    validateCrewAccess(crewId, memberUuid);

    return settlementRepository.findByCrewId(crewId);
  }

  public void validateDetailQueryInput(Long settlementId, UUID memberUuid) {
    if (settlementId == null || settlementId <= 0L || memberUuid == null) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
  }

  public Settlement requireAccessibleSettlement(Long settlementId, UUID memberUuid) {
    validateDetailQueryInput(settlementId, memberUuid);

    Settlement settlement =
        settlementRepository
            .findById(settlementId)
            .orElseThrow(() -> new CustomException(SettlementErrorCode.SETTLEMENT_NOT_FOUND));

    validateCrewAccess(settlement.getCrew().getId(), memberUuid);
    return settlement;
  }

  public void validateCrewAccess(Long crewId, UUID memberUuid) {
    if (crewRepository.existsByIdAndHostMemberUuid(crewId, memberUuid)) {
      return;
    }

    if (crewParticipantRepository.existsByCrewIdAndMemberUuidAndStatus(
        crewId, memberUuid, CrewParticipantStatus.LOCKED)) {
      return;
    }

    throw new CustomException(CrewErrorCode.CREW_ACCESS_DENIED);
  }
}
