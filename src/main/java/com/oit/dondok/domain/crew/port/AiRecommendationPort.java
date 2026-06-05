package com.oit.dondok.domain.crew.port;

import com.oit.dondok.domain.crew.dto.response.AiRecommendationResponse.DraftResponse;

public interface AiRecommendationPort {

  DraftResponse requestDraft(String seedText);
}
