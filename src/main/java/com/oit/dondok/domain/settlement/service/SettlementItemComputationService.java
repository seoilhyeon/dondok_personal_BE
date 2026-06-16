package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.repository.MissionLogRepository;
import com.oit.dondok.domain.mission.repository.MissionRuleRepository;
import com.oit.dondok.domain.settlement.entity.DailySettlementParticipantSnapshot;
import com.oit.dondok.domain.settlement.entity.DailySettlementPhase;
import com.oit.dondok.domain.settlement.entity.DailySettlementSnapshot;
import com.oit.dondok.domain.settlement.entity.DailySettlementStatus;
import com.oit.dondok.domain.settlement.entity.RemainderPolicy;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementCalculationReason;
import com.oit.dondok.domain.settlement.entity.SettlementFailureCode;
import com.oit.dondok.domain.settlement.entity.SettlementItem;
import com.oit.dondok.domain.settlement.repository.DailySettlementParticipantSnapshotRepository;
import com.oit.dondok.domain.settlement.repository.DailySettlementSnapshotRepository;
import com.oit.dondok.domain.settlement.repository.SettlementItemRepository;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import com.oit.dondok.domain.settlement.service.model.SettlementCalculationInput;
import com.oit.dondok.domain.settlement.service.model.SettlementCalculationResult;
import com.oit.dondok.domain.settlement.service.model.SettlementParticipantInput;
import com.oit.dondok.domain.settlement.service.model.SettlementParticipantResult;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementItemComputationService {

  private final CrewParticipantRepository crewParticipantRepository;
  private final MissionRuleRepository missionRuleRepository;
  private final MissionLogRepository missionLogRepository;
  private final FinalSettlementMissionDateResolver missionDateResolver;
  private final DailySettlementSnapshotRepository dailySettlementSnapshotRepository;
  private final DailySettlementParticipantSnapshotRepository
      dailySettlementParticipantSnapshotRepository;
  private final SettlementRepository settlementRepository;
  private final SettlementItemRepository settlementItemRepository;
  private final SettlementCalculatorService settlementCalculatorService;

  public SettlementItemComputationService(
      CrewParticipantRepository crewParticipantRepository,
      MissionRuleRepository missionRuleRepository,
      MissionLogRepository missionLogRepository,
      FinalSettlementMissionDateResolver missionDateResolver,
      DailySettlementSnapshotRepository dailySettlementSnapshotRepository,
      DailySettlementParticipantSnapshotRepository dailySettlementParticipantSnapshotRepository,
      SettlementRepository settlementRepository,
      SettlementItemRepository settlementItemRepository,
      SettlementCalculatorService settlementCalculatorService) {
    this.crewParticipantRepository = crewParticipantRepository;
    this.missionRuleRepository = missionRuleRepository;
    this.missionLogRepository = missionLogRepository;
    this.missionDateResolver = missionDateResolver;
    this.dailySettlementSnapshotRepository = dailySettlementSnapshotRepository;
    this.dailySettlementParticipantSnapshotRepository =
        dailySettlementParticipantSnapshotRepository;
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
                Collectors.toMap(
                    SettlementParticipantResult::participantKey,
                    result -> result,
                    (first, second) -> {
                      throw new SettlementBatchRunFailure(
                          SettlementFailureCode.CALCULATION_FAILED,
                          "정산 계산 결과에서 중복된 참여자 키가 발견되었습니다. participantKey="
                              + first.participantKey());
                    },
                    LinkedHashMap::new));

    validateCalculationResultParticipantKeys(
        participants, resultsByParticipantKey.keySet(), settlement.getCrew().getId());

    for (CrewParticipant participant : participants) {
      SettlementParticipantResult result = resultsByParticipantKey.get(participant.getId());
      if (result == null) {
        throw new SettlementBatchRunFailure(
            SettlementFailureCode.CALCULATION_FAILED,
            "정산 계산 결과에서 계산 값을 찾을 수 없습니다. participantId=" + participant.getId());
      }
      upsertSettlementItem(settlement, participant, result, crew.getStartAt(), crew.getEndAt());
    }

    return settlementItemRepository.findBySettlementIdOrderByIdAsc(settlementId).stream()
        .map(SettlementItem::getId)
        .toList();
  }

  private void validateCalculationResultParticipantKeys(
      List<CrewParticipant> participants, Set<Long> resultParticipantKeys, Long crewId) {
    Set<Long> participantIds =
        participants.stream().map(CrewParticipant::getId).collect(Collectors.toSet());
    if (!participantIds.equals(resultParticipantKeys)) {
      Set<Long> missingIds = new LinkedHashSet<>(participantIds);
      missingIds.removeAll(resultParticipantKeys);
      Set<Long> extraIds = new LinkedHashSet<>(resultParticipantKeys);
      extraIds.removeAll(participantIds);

      throw new SettlementBatchRunFailure(
          SettlementFailureCode.CALCULATION_FAILED,
          "정산 계산 결과 참여자 키 집합이 기준 참여자 목록과 일치하지 않습니다. "
              + "crewId="
              + crewId
              + ", missing="
              + missingIds
              + ", extra="
              + extraIds);
    }
  }

  private Settlement requireSettlement(Long settlementId) {
    return settlementRepository
        .findById(settlementId)
        .orElseThrow(
            () -> new IllegalStateException("정산 정보를 찾을 수 없습니다. settlementId=" + settlementId));
  }

  private SettlementCalculationResult calculateSettlement(
      Crew crew, List<CrewParticipant> participants) {
    MissionRule missionRule = requireMissionRule(crew.getId());
    List<LocalDate> missionDates = missionDateResolver.resolveMissionDates(crew, missionRule);
    if (missionDates.isEmpty()) {
      throw new SettlementBatchRunFailure(
          SettlementFailureCode.INPUT_LOAD_FAILED,
          "최종 정산에 사용할 예정 미션일을 찾을 수 없습니다. crewId=" + crew.getId());
    }

    Map<LocalDate, DailySettlementSnapshot> snapshotsByMissionDate =
        loadFinalizedSnapshotsByMissionDate(crew, missionRule, missionDates);
    DailySettlementSnapshot lastSnapshot =
        snapshotsByMissionDate.get(missionDates.get(missionDates.size() - 1));
    Map<Long, Integer> recognizedCountsByParticipantId =
        loadRecognizedCountsByParticipantId(crew.getId(), participants, lastSnapshot);
    Map<Long, Integer> rawCountsByParticipantId =
        loadRawCountsByParticipantId(crew, participants, lastSnapshot);

    try {
      return settlementCalculatorService.calculate(
          new SettlementCalculationInput(
              RemainderPolicy.HOST_REMAINDER,
              participants.stream()
                  .map(
                      participant ->
                          toParticipantInput(
                              crew,
                              participant,
                              rawCountsByParticipantId.getOrDefault(participant.getId(), 0),
                              recognizedCountsByParticipantId.getOrDefault(participant.getId(), 0)))
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

  private MissionRule requireMissionRule(Long crewId) {
    return missionRuleRepository
        .findByCrewId(crewId)
        .orElseThrow(
            () ->
                new SettlementBatchRunFailure(
                    SettlementFailureCode.INPUT_LOAD_FAILED,
                    "최종 정산에 사용할 미션 규칙을 찾을 수 없습니다. crewId=" + crewId));
  }

  private Map<LocalDate, DailySettlementSnapshot> loadFinalizedSnapshotsByMissionDate(
      Crew crew, MissionRule missionRule, List<LocalDate> missionDates) {
    Map<LocalDate, DailySettlementSnapshot> snapshotsByMissionDate =
        dailySettlementSnapshotRepository
            .findByCrewIdAndDailySettlementTypeAndPhaseAndStatusAndMissionDateIn(
                crew.getId(),
                missionRule.getDailySettlementType(),
                DailySettlementPhase.FINALIZED,
                DailySettlementStatus.SUCCEEDED,
                missionDates)
            .stream()
            .collect(
                Collectors.toMap(
                    DailySettlementSnapshot::getMissionDate,
                    Function.identity(),
                    (first, second) -> {
                      throw new SettlementBatchRunFailure(
                          SettlementFailureCode.INPUT_LOAD_FAILED,
                          "동일한 미션일의 일일 정산 스냅샷이 중복 조회되었습니다. missionDate=" + first.getMissionDate());
                    }));

    if (!snapshotsByMissionDate.keySet().containsAll(missionDates)) {
      Set<LocalDate> missingDates = new LinkedHashSet<>(missionDates);
      missingDates.removeAll(snapshotsByMissionDate.keySet());
      throw new SettlementBatchRunFailure(
          SettlementFailureCode.INPUT_LOAD_FAILED,
          "최종 정산에 필요한 일일 정산 스냅샷이 누락되었습니다. crewId="
              + crew.getId()
              + ", missingDates="
              + missingDates);
    }
    return snapshotsByMissionDate;
  }

  private Map<Long, Integer> loadRecognizedCountsByParticipantId(
      Long crewId, List<CrewParticipant> participants, DailySettlementSnapshot snapshot) {
    Set<Long> participantIds =
        participants.stream().map(CrewParticipant::getId).collect(Collectors.toSet());
    List<DailySettlementParticipantSnapshot> participantSnapshots =
        dailySettlementParticipantSnapshotRepository.findByDailySettlementSnapshotIdIn(
            List.of(snapshot.getId()));

    Map<Long, Integer> successCountsByParticipantId =
        participantSnapshots.stream()
            .collect(
                Collectors.toMap(
                    participantSnapshot -> participantSnapshot.getCrewParticipant().getId(),
                    DailySettlementParticipantSnapshot::getSuccessCount,
                    (first, second) -> {
                      throw new SettlementBatchRunFailure(
                          SettlementFailureCode.CALCULATION_FAILED,
                          "최종 정산 스냅샷에 중복 참여자 결과가 포함되어 있습니다. crewId="
                              + crewId
                              + ", snapshotId="
                              + snapshot.getId());
                    }));

    Set<Long> extraIds = new HashSet<>(successCountsByParticipantId.keySet());
    extraIds.removeAll(participantIds);
    if (!extraIds.isEmpty()) {
      throw new SettlementBatchRunFailure(
          SettlementFailureCode.CALCULATION_FAILED,
          "최종 정산 스냅샷에 기준 참여자 목록에 없는 참여자가 포함되어 있습니다. crewId=" + crewId + ", extra=" + extraIds);
    }

    Set<Long> missingIds = new LinkedHashSet<>(participantIds);
    missingIds.removeAll(successCountsByParticipantId.keySet());
    if (!missingIds.isEmpty()) {
      throw new SettlementBatchRunFailure(
          SettlementFailureCode.INPUT_LOAD_FAILED,
          "최종 정산에 필요한 참여자 일일 정산 스냅샷이 누락되었습니다. crewId=" + crewId + ", missing=" + missingIds);
    }

    return successCountsByParticipantId;
  }

  private Map<Long, Integer> loadRawCountsByParticipantId(
      Crew crew, List<CrewParticipant> participants, DailySettlementSnapshot lastSnapshot) {
    Set<Long> participantIds =
        participants.stream().map(CrewParticipant::getId).collect(Collectors.toSet());
    Map<Long, Integer> rawCountsByParticipantId =
        missionLogRepository
            .findApprovedLogCandidatesForDailySettlementProjection(
                crew.getId(),
                crew.getStartAt(),
                lastSnapshot.getMissionDate().plusDays(1).atStartOfDay())
            .stream()
            .collect(
                Collectors.groupingBy(
                    missionLog -> missionLog.getCrewParticipant().getId(),
                    Collectors.collectingAndThen(Collectors.toList(), List::size)));

    Set<Long> extraIds = new HashSet<>(rawCountsByParticipantId.keySet());
    extraIds.removeAll(participantIds);
    if (!extraIds.isEmpty()) {
      throw new SettlementBatchRunFailure(
          SettlementFailureCode.CALCULATION_FAILED,
          "최종 정산 승인 로그에 기준 참여자 목록에 없는 참여자가 포함되어 있습니다. crewId="
              + crew.getId()
              + ", extra="
              + extraIds);
    }
    return rawCountsByParticipantId;
  }

  private SettlementParticipantInput toParticipantInput(
      Crew crew, CrewParticipant participant, int successCountRaw, int recognizedSuccessCount) {
    if (successCountRaw < recognizedSuccessCount) {
      throw new SettlementBatchRunFailure(
          SettlementFailureCode.CALCULATION_FAILED,
          "최종 정산 승인 로그 수가 일일 정산 스냅샷 인정 성공 수보다 작습니다. crewId="
              + crew.getId()
              + ", participantId="
              + participant.getId());
    }
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
