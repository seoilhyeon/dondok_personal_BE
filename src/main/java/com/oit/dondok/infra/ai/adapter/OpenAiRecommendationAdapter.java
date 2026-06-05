package com.oit.dondok.infra.ai.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oit.dondok.domain.crew.dto.response.AiRecommendationResponse.DraftResponse;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.port.AiRecommendationPort;
import com.oit.dondok.global.exception.CustomException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiRecommendationAdapter implements AiRecommendationPort {

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;

  @Value("${openai.api-key}")
  private String apiKey;

  @Value("${openai.model:gpt-4o-mini}")
  private String model;

  private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

  private static final String SYSTEM_PROMPT =
      """
      You are a habit-forming platform helper. Respond ONLY with a raw JSON object matching this schema:
      {
        "title": "String (max 20 chars)",
        "description": "String (max 100 chars)",
        "frequency_type": "DAILY or SPECIFIC_DAYS",
        "mission_schedule_days": ["MONDAY", "TUESDAY", etc],
        "daily_settlement_type": "A, B, or C",
        "deposit_amount": Number,
        "duration_days": Number
      }
      Do not write any markdown codeblock wrapper, prose, or explanation.
      """;

  @Override
  public DraftResponse requestDraft(String seedText) {
    String rawResponse = callOpenAi(seedText);
    return parseResponse(rawResponse);
  }

  private String callOpenAi(String seedText) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setBearerAuth(apiKey);

      Map<String, Object> requestBody =
          Map.of(
              "model",
              model,
              "messages",
              List.of(
                  Map.of("role", "system", "content", SYSTEM_PROMPT),
                  Map.of("role", "user", "content", seedText)),
              "temperature",
              0.2);

      HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
      return restTemplate.postForObject(OPENAI_URL, entity, String.class);
    } catch (RestClientException e) {
      log.error("[AI] OpenAI API call failed: {}", e.getMessage(), e);
      throw new CustomException(CrewErrorCode.AI_RECOMMENDATION_FAILED, e);
    }
  }

  private DraftResponse parseResponse(String rawResponse) {
    try {
      String content =
          objectMapper
              .readTree(rawResponse)
              .path("choices")
              .get(0)
              .path("message")
              .path("content")
              .asText();
      return objectMapper.readValue(content, DraftResponse.class);
    } catch (JsonProcessingException e) {
      log.error("[AI] OpenAI response parse failed: {}", e.getMessage(), e);
      throw new CustomException(CrewErrorCode.AI_RESPONSE_INVALID, e);
    } catch (Exception e) {
      log.error("[AI] Unexpected error while parsing OpenAI response: {}", e.getMessage(), e);
      throw new CustomException(CrewErrorCode.AI_RESPONSE_INVALID, e);
    }
  }
}
