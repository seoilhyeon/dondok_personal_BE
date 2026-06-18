package com.oit.dondok.domain.settlement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.crew.repository.CrewQueryRepository;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.exception.MissionErrorCode;
import com.oit.dondok.domain.mission.repository.MissionRuleRepository;
import com.oit.dondok.domain.settlement.dto.response.SettlementDetailResponse;
import com.oit.dondok.domain.settlement.dto.response.SettlementItemDetailResponse;
import com.oit.dondok.domain.settlement.dto.response.SettlementMeResponse;
import com.oit.dondok.domain.settlement.dto.response.SettlementSummaryResponse;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementCalculationReason;
import com.oit.dondok.domain.settlement.entity.SettlementItem;
import com.oit.dondok.domain.settlement.repository.SettlementItemRepository;
import com.oit.dondok.domain.settlement.repository.SettlementMeProjection;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import com.oit.dondok.global.util.SeoulDateTimeUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SettlementQueryService {

  private final SettlementItemRepository settlementItemRepository;
  private final SettlementQueryGuard settlementQueryGuard;
  private final ObjectMapper objectMapper;
  private final MissionRuleRepository missionRuleRepository;
  private final CrewQueryRepository crewQueryRepository;
  private final CrewParticipantRepository crewParticipantRepository;
  private final CrewMissionStatsCalculator crewMissionStatsCalculator;

  @Transactional(readOnly = true)
  public SettlementSummaryResponse getSettlementSummary(Long crewId, UUID memberUuid) {
    Settlement settlement =
        settlementQueryGuard.authorizeSummaryQuery(crewId, memberUuid).orElse(null);

    return settlement == null
        ? SettlementSummaryResponse.ofCrewSettlementMissing(crewId)
        : SettlementSummaryResponse.of(settlement);
  }

  @Transactional(readOnly = true)
  public SettlementDetailResponse getSettlementDetail(Long settlementId, UUID memberUuid) {
    Settlement settlement =
        settlementQueryGuard.requireAccessibleSettlement(settlementId, memberUuid);
    Crew crew = settlement.getCrew();

    List<SettlementItem> items =
        settlementItemRepository.findBySettlementIdOrderByIdAsc(settlement.getId());

    // calculation_reason 검증/파싱을 먼저 수행해 잘못된 데이터는 빠르게 실패시킨다.
    Map<Long, JsonNode> reasonByItemId = new LinkedHashMap<>();
    for (SettlementItem item : items) {
      reasonByItemId.put(item.getId(), toCalculationReason(item.getCalculationReason()));
    }

    Long myParticipantId =
        crewParticipantRepository
            .findByCrewIdAndMemberUuid(crew.getId(), memberUuid)
            .map(CrewParticipant::getId)
            .orElse(null);
    Map<Long, Integer> rankByParticipantId = computeRanks(items);

    List<SettlementItemDetailResponse> itemResponses =
        items.stream()
            .map(
                item -> {
                  long participantId = item.getCrewParticipant().getId();
                  return SettlementItemDetailResponse.from(
                      item,
                      reasonByItemId.get(item.getId()),
                      item.getMember().getNickname(),
                      rankByParticipantId.get(participantId),
                      myParticipantId != null && myParticipantId == participantId);
                })
            .toList();

    Integer myRank = myParticipantId == null ? null : rankByParticipantId.get(myParticipantId);
    int missionDays = resolveMissionDays(crew);
    String crewSuccessRate =
        crewMissionStatsCalculator.crewSuccessRate(
            settlement.getTotalRecognizedSuccess(), settlement.getTotalParticipants(), missionDays);

    return SettlementDetailResponse.of(
        settlement, missionDays, crewSuccessRate, myRank, itemResponses);
  }

  @Transactional(readOnly = true)
  public SettlementMeResponse getSettlementMe(Long settlementId, UUID memberUuid) {
    SettlementMeProjection projection =
        settlementQueryGuard.requireAccessibleSettlementMe(settlementId, memberUuid);

    SettlementItemDetailResponse myItem = mapSettlementMyItem(projection);

    return new SettlementMeResponse(
        projection.settlementId(),
        projection.crewId(),
        projection.crewName(),
        projection.crewStartedAt() == null ? null : projection.crewStartedAt().toLocalDate(),
        projection.crewEndedAt() == null ? null : projection.crewEndedAt().toLocalDate(),
        projection.status().name(),
        projection.retryCount(),
        projection.failureCode() == null ? null : projection.failureCode().name(),
        projection.failureMessage(),
        SeoulDateTimeUtils.toSeoulOffset(projection.startedAt()),
        SeoulDateTimeUtils.toSeoulOffset(projection.finishedAt()),
        myItem);
  }

  // share_ratio DESC, 동률이면 crew_participant_id ASC 정렬 + 공동 순위
  private Map<Long, Integer> computeRanks(List<SettlementItem> items) {
    List<SettlementItem> sorted =
        items.stream()
            .sorted(
                Comparator.comparing(SettlementItem::getShareRatio, Comparator.reverseOrder())
                    .thenComparing(item -> item.getCrewParticipant().getId()))
            .toList();
    Map<Long, Integer> rankByParticipantId = new LinkedHashMap<>();
    BigDecimal previousRatio = null;
    int previousRank = 0;
    for (int index = 0; index < sorted.size(); index++) {
      SettlementItem item = sorted.get(index);
      int rank;
      if (previousRatio != null && item.getShareRatio().compareTo(previousRatio) == 0) {
        rank = previousRank;
      } else {
        rank = index + 1;
        previousRank = rank;
        previousRatio = item.getShareRatio();
      }
      rankByParticipantId.put(item.getCrewParticipant().getId(), rank);
    }
    return rankByParticipantId;
  }

  private int resolveMissionDays(Crew crew) {
    MissionRule missionRule =
        missionRuleRepository
            .findByCrewId(crew.getId())
            .orElseThrow(() -> new CustomException(MissionErrorCode.MISSION_RULE_NOT_FOUND));
    return crewMissionStatsCalculator.missionDays(
        crew.getStartAt().toLocalDate(),
        crew.getEndAt().toLocalDate(),
        missionRule.getFrequencyType(),
        resolveScheduleDays(missionRule));
  }

  private Set<DayOfWeek> resolveScheduleDays(MissionRule missionRule) {
    if (missionRule.getFrequencyType() != MissionFrequencyType.SPECIFIC_DAYS) {
      return Set.of();
    }
    return crewQueryRepository
        .findScheduleDaysByRuleIds(List.of(missionRule.getId()))
        .getOrDefault(missionRule.getId(), List.of())
        .stream()
        .map(DayOfWeek::valueOf)
        .collect(Collectors.toUnmodifiableSet());
  }

  private SettlementItemDetailResponse mapSettlementMyItem(SettlementMeProjection projection) {
    if (projection.settlementItemId() == null) {
      return null;
    }

    return new SettlementItemDetailResponse(
        projection.settlementItemId(),
        projection.crewParticipantId(),
        null,
        true,
        projection.participantStatusSnapshot(),
        projection.depositAmount(),
        projection.successCountRaw(),
        projection.recognizedSuccessCount(),
        projection.recognizedDatesCount(),
        projection.excludedSuccessCount(),
        projection.shareRatio() == null
            ? null
            : projection.shareRatio().setScale(6, RoundingMode.FLOOR).toPlainString(),
        null,
        projection.baseRefundAmount(),
        projection.remainderBonusAmount(),
        projection.refundAmount(),
        projection.pointHistoryId(),
        parseCalculationReason(projection.calculationReason()));
  }

  private JsonNode toCalculationReason(SettlementCalculationReason calculationReason) {
    JsonNode reason = calculationReason == null ? null : calculationReason.toJsonNode();
    if (reason == null) {
      throw new CustomException(GlobalErrorCode.SERVER_ERROR);
    }
    if (!reason.isObject()) {
      throw new CustomException(GlobalErrorCode.SERVER_ERROR);
    }
    return reason;
  }

  private JsonNode parseCalculationReason(String calculationReason) {
    if (!StringUtils.hasText(calculationReason)) {
      throw new CustomException(GlobalErrorCode.SERVER_ERROR);
    }

    try {
      JsonNode reasonNode = objectMapper.readTree(calculationReason);
      if (!reasonNode.isObject()) {
        throw new CustomException(GlobalErrorCode.SERVER_ERROR);
      }
      return reasonNode;
    } catch (JsonProcessingException e) {
      throw new CustomException(GlobalErrorCode.SERVER_ERROR, e);
    }
  }
}
