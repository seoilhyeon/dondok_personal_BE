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

  private final AiRecommendationPort aiRecommendationPort;

  @Transactional(readOnly = true)
  public AiRecommendationResponse getRecommendation(AiRecommendationRequest request) {
    DraftResponse rawDraft = aiRecommendationPort.requestDraft(request.seedText());

    List<WarningResponse> warnings = new ArrayList<>();
    long validatedDeposit = rawDraft.depositAmount();

    if (validatedDeposit % 1000 != 0) {
      validatedDeposit = ((validatedDeposit / 1000) + 1) * 1000;
      warnings.add(new WarningResponse("deposit_amount", "권장 보증금은 1,000원 단위로 조정되었습니다."));
    }

    DraftResponse finalDraft =
        new DraftResponse(
            rawDraft.title(),
            rawDraft.description(),
            rawDraft.frequencyType(),
            rawDraft.missionScheduleDays(),
            rawDraft.dailySettlementType(),
            validatedDeposit,
            rawDraft.durationDays());

    return new AiRecommendationResponse(finalDraft, warnings);
  }
}
