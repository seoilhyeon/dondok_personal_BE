package com.oit.dondok.domain.settlement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
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

    List<SettlementItemDetailResponse> items =
        settlementItemRepository.findBySettlementIdOrderByIdAsc(settlement.getId()).stream()
            .map(this::mapSettlementItem)
            .toList();

    return SettlementDetailResponse.of(settlement, items);
  }

  @Transactional(readOnly = true)
  public SettlementMeResponse getSettlementMe(Long settlementId, UUID memberUuid) {
    SettlementMeProjection projection =
        settlementQueryGuard.requireAccessibleSettlementMe(settlementId, memberUuid);

    SettlementItemDetailResponse myItem = mapSettlementMyItem(projection);

    return new SettlementMeResponse(
        projection.settlementId(),
        projection.crewId(),
        projection.status().name(),
        projection.retryCount(),
        projection.failureCode() == null ? null : projection.failureCode().name(),
        projection.failureMessage(),
        SeoulDateTimeUtils.toSeoulOffset(projection.startedAt()),
        SeoulDateTimeUtils.toSeoulOffset(projection.finishedAt()),
        myItem);
  }

  private SettlementItemDetailResponse mapSettlementItem(SettlementItem item) {
    return SettlementItemDetailResponse.from(
        item, toCalculationReason(item.getCalculationReason()));
  }

  private SettlementItemDetailResponse mapSettlementMyItem(SettlementMeProjection projection) {
    if (projection.settlementItemId() == null) {
      return null;
    }

    return new SettlementItemDetailResponse(
        projection.settlementItemId(),
        projection.crewParticipantId(),
        projection.participantStatusSnapshot(),
        projection.depositAmount(),
        projection.successCountRaw(),
        projection.recognizedSuccessCount(),
        projection.recognizedDatesCount(),
        projection.excludedSuccessCount(),
        projection.shareRatio() == null
            ? null
            : projection.shareRatio().setScale(6, RoundingMode.FLOOR).toPlainString(),
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
