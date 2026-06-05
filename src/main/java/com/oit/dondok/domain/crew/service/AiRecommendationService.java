package com.oit.dondok.domain.crew.service;

import com.oit.dondok.domain.crew.dto.request.AiRecommendationRequest;
import com.oit.dondok.domain.crew.dto.response.AiRecommendationResponse;
import com.oit.dondok.domain.crew.dto.response.AiRecommendationResponse.DraftResponse;
import com.oit.dondok.domain.crew.dto.response.AiRecommendationResponse.WarningResponse;
import com.oit.dondok.domain.crew.port.AiRecommendationPort;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiRecommendationService {

  private static final long MIN_DEPOSIT = 1_000L;
  private static final long MAX_DEPOSIT = 100_000L;

  private final AiRecommendationPort aiRecommendationPort;

  @Transactional(readOnly = true)
  public AiRecommendationResponse getRecommendation(AiRecommendationRequest request) {
    DraftResponse rawDraft = aiRecommendationPort.requestDraft(request.seedText());

    List<WarningResponse> warnings = new ArrayList<>();
    long deposit = rawDraft.depositAmount();

    // 1. 1,000원 단위 올림 처리
    if (deposit % 1000 != 0) {
      deposit = ((deposit / 1000) + 1) * 1000;
      warnings.add(new WarningResponse("deposit_amount", "권장 보증금은 1,000원 단위로 조정되었습니다."));
    }

    // 2. MIN/MAX 범위 클램핑
    long clamped = Math.min(Math.max(deposit, MIN_DEPOSIT), MAX_DEPOSIT);
    if (clamped != deposit) {
      warnings.add(
          new WarningResponse(
              "deposit_amount", "보증금이 허용 범위(" + MIN_DEPOSIT + "~" + MAX_DEPOSIT + "원)로 조정되었습니다."));
      deposit = clamped;
    }

    DraftResponse finalDraft =
        new DraftResponse(
            rawDraft.title(),
            rawDraft.description(),
            rawDraft.frequencyType(),
            rawDraft.missionScheduleDays(),
            rawDraft.dailySettlementType(),
            deposit,
            rawDraft.durationDays());

    return new AiRecommendationResponse(finalDraft, warnings);
  }
}
