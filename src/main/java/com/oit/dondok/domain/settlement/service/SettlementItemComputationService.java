package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.mission.entity.CertificationStatus;
import com.oit.dondok.domain.mission.entity.MissionLog;
import com.oit.dondok.domain.mission.repository.MissionLogRepository;
import com.oit.dondok.domain.settlement.entity.RemainderPolicy;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementCalculationReason;
import com.oit.dondok.domain.settlement.entity.SettlementFailureCode;
import com.oit.dondok.domain.settlement.entity.SettlementItem;
import com.oit.dondok.domain.settlement.repository.SettlementItemRepository;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import com.oit.dondok.domain.settlement.service.model.SettlementCalculationInput;
import com.oit.dondok.domain.settlement.service.model.SettlementCalculationResult;
import com.oit.dondok.domain.settlement.service.model.SettlementParticipantInput;
import com.oit.dondok.domain.settlement.service.model.SettlementParticipantResult;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementItemComputationService {

  private final CrewParticipantRepository crewParticipantRepository;
  private final MissionLogRepository missionLogRepository;
  private final SettlementRepository settlementRepository;
  private final SettlementItemRepository settlementItemRepository;
  private final SettlementCalculatorService settlementCalculatorService;

  public SettlementItemComputationService(
      CrewParticipantRepository crewParticipantRepository,
      MissionLogRepository missionLogRepository,
      SettlementRepository settlementRepository,
      SettlementItemRepository settlementItemRepository,
      SettlementCalculatorService settlementCalculatorService) {
    this.crewParticipantRepository = crewParticipantRepository;
    this.missionLogRepository = missionLogRepository;
    this.settlementRepository = settlementRepository;
    this.settlementItemRepository = settlementItemRepository;
    this.settlementCalculatorService = settlementCalculatorService;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Long> ensureSettlementItems(Long settlementId) {
    Settlement settlement = requireSettlement(settlementId);
    Crew crew = settlement.getCrew();
    List<CrewParticipant> participants =
        crewParticipantRepository.findByCrewIdAndStatus(crew.getId(), CrewParticipantStatus.LOCKED);
    if (participants.isEmpty()) {
      throw new SettlementBatchRunFailure(
          SettlementFailureCode.INPUT_LOAD_FAILED,
          "정산 계산 대상 참여자를 찾을 수 없습니다. LOCKED 상태의 crewParticipant가 필요합니다. crewId=" + crew.getId());
    }

    SettlementCalculationResult calculationResult = calculateSettlement(crew, participants);
    settlement.updateTotals(
        calculationResult.totalParticipants(),
        calculationResult.totalLockedAmount(),
        calculationResult.totalRecognizedSuccess(),
        calculationResult.totalBaseRefundAmount(),
        calculationResult.totalRemainderAmount(),
        calculationResult.remainderPolicy());

    Map<Long, SettlementParticipantResult> resultsByParticipantKey =
        calculationResult.participants().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    SettlementParticipantResult::participantKey,
                    result -> result,
                    (first, second) -> first,
                    LinkedHashMap::new));

    for (CrewParticipant participant : participants) {
      SettlementParticipantResult result = resultsByParticipantKey.get(participant.getId());
      if (result == null) {
        throw new SettlementBatchRunFailure(
            SettlementFailureCode.CALCULATION_FAILED,
            "참여자 계산 결과가 없습니다. participantId=" + participant.getId());
      }
      upsertSettlementItem(settlement, participant, result, crew.getStartAt(), crew.getEndAt());
    }

    return settlementItemRepository.findBySettlementIdOrderByIdAsc(settlementId).stream()
        .map(SettlementItem::getId)
        .toList();
  }

  private Settlement requireSettlement(Long settlementId) {
    return settlementRepository
        .findById(settlementId)
        .orElseThrow(
            () -> new IllegalStateException("정산 정보를 찾을 수 없습니다. settlementId=" + settlementId));
  }

  private SettlementCalculationResult calculateSettlement(
      Crew crew, List<CrewParticipant> participants) {
    try {
      return settlementCalculatorService.calculate(
          new SettlementCalculationInput(
              RemainderPolicy.HOST_REMAINDER,
              participants.stream()
                  .map(participant -> toParticipantInput(crew, participant))
                  .toList()));
    } catch (RuntimeException exception) {
      if (exception instanceof SettlementBatchRunFailure settlementFailure) {
        throw settlementFailure;
      }
      throw new SettlementBatchRunFailure(
          SettlementFailureCode.CALCULATION_FAILED,
          "정산 계산 중 예기치 못한 오류가 발생했습니다. crewId=" + crew.getId(),
          exception);
    }
  }

  private SettlementParticipantInput toParticipantInput(Crew crew, CrewParticipant participant) {
    List<MissionLog> successLogs = findSuccessLogs(crew, participant);
    int successCountRaw = successLogs.size();
    int recognizedSuccessCount = countDistinctSuccessDates(successLogs);
    boolean host = participant.getMember().getId().equals(crew.getHostMember().getId());
    return new SettlementParticipantInput(
        participant.getId(),
        host,
        participant.getDepositAmount(),
        successCountRaw,
        recognizedSuccessCount,
        recognizedSuccessCount,
        successCountRaw - recognizedSuccessCount);
  }

  private List<MissionLog> findSuccessLogs(Crew crew, CrewParticipant participant) {
    return missionLogRepository
        .findByCrewParticipantIdAndCertificationStatusAndServerTimeGreaterThanEqualAndServerTimeLessThanEqual(
            participant.getId(), CertificationStatus.SUCCESS, crew.getStartAt(), crew.getEndAt());
  }

  private int countDistinctSuccessDates(List<MissionLog> successLogs) {
    return (int)
        successLogs.stream().map(log -> log.getServerTime().toLocalDate()).distinct().count();
  }

  private void upsertSettlementItem(
      Settlement settlement,
      CrewParticipant participant,
      SettlementParticipantResult result,
      LocalDateTime periodStartAt,
      LocalDateTime periodEndAt) {
    Optional<SettlementItem> existing =
        settlementItemRepository.findBySettlementIdAndCrewParticipantId(
            settlement.getId(), participant.getId());
    if (existing.isPresent()) {
      if (!matchesCalculation(existing.get(), result, periodStartAt, periodEndAt)) {
        throw new SettlementBatchRunFailure(
            SettlementFailureCode.CALCULATION_FAILED,
            "기존 정산 항목과 계산 결과가 일치하지 않습니다. settlementItemId=" + existing.get().getId());
      }
      return;
    }

    settlementItemRepository.save(
        SettlementItem.create(
            settlement,
            participant,
            result.depositAmount(),
            result.successCountRaw(),
            result.recognizedSuccessCount(),
            result.recognizedDatesCount(),
            result.excludedSuccessCount(),
            periodStartAt,
            periodEndAt,
            result.shareRatio(),
            result.baseRefundAmount(),
            result.remainderBonusAmount(),
            result.refundAmount(),
            SettlementCalculationReason.of(result),
            "{}",
            "{}"));
  }

  private boolean matchesCalculation(
      SettlementItem item,
      SettlementParticipantResult result,
      LocalDateTime periodStartAt,
      LocalDateTime periodEndAt) {
    return item.matchesCalculation(
        result.depositAmount(),
        result.successCountRaw(),
        result.recognizedSuccessCount(),
        result.recognizedDatesCount(),
        result.excludedSuccessCount(),
        periodStartAt,
        periodEndAt,
        result.shareRatio(),
        result.baseRefundAmount(),
        result.remainderBonusAmount(),
        result.refundAmount());
  }
}
