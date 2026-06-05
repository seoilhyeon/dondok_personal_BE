package com.oit.dondok.domain.crew.controller;

import com.oit.dondok.domain.crew.dto.request.AiRecommendationRequest;
import com.oit.dondok.domain.crew.dto.response.AiRecommendationResponse;
import com.oit.dondok.domain.crew.service.AiRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiRecommendationController {

  private final AiRecommendationService aiRecommendationService;

  @Operation(
      summary = "AI 크루 생성 도우미",
      description = "목표 텍스트를 기반으로 AI가 미션 설정 초안을 추천합니다. 결과는 draft이며 자동 저장되지 않습니다.",
      security = @SecurityRequirement(name = "bearerAuth"))
  @PostMapping("/mission-recommendations")
  public ResponseEntity<AiRecommendationResponse> createMissionRecommendation(
      @Valid @RequestBody AiRecommendationRequest request) {
    return ResponseEntity.ok(aiRecommendationService.getRecommendation(request));
  }
}
