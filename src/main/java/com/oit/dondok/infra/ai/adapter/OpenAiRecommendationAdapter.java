package com.oit.dondok.infra.ai.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.port.AiDraft;
import com.oit.dondok.domain.crew.port.AiRecommendationPort;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.infra.ai.config.OpenAiProperties;
import java.time.DayOfWeek;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

  private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
  private static final Set<String> VALID_FREQUENCY_TYPES = Set.of("DAILY", "SPECIFIC_DAYS");
  private static final Set<String> VALID_SETTLEMENT_TYPES = Set.of("A", "B", "C");

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

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;
  private final OpenAiProperties openAiProperties;

  @Override
  public AiDraft requestDraft(String seedText) {
    String rawResponse = callOpenAi(seedText);
    OpenAiDraftResult result = parseResponse(rawResponse);
    validate(result);
    return toAiDraft(result);
  }

  private String callOpenAi(String seedText) {
    if (openAiProperties.apiKey() == null || openAiProperties.apiKey().isBlank()) {
      log.error("[AI] OpenAI API key is not configured");
      throw new CustomException(CrewErrorCode.AI_RECOMMENDATION_FAILED);
    }

    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setBearerAuth(openAiProperties.apiKey());

      Map<String, Object> requestBody =
          Map.of(
              "model",
              openAiProperties.model(),
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

  private OpenAiDraftResult parseResponse(String rawResponse) {
    try {
      String content =
          objectMapper
              .readTree(rawResponse)
              .path("choices")
              .get(0)
              .path("message")
              .path("content")
              .asText();
      OpenAiDraftResult result = objectMapper.readValue(content, OpenAiDraftResult.class);
      if (result == null) {
        throw new CustomException(CrewErrorCode.AI_RESPONSE_INVALID);
      }
      return result;
    } catch (JsonProcessingException e) {
      log.error("[AI] OpenAI response parse failed: {}", e.getMessage(), e);
      throw new CustomException(CrewErrorCode.AI_RESPONSE_INVALID, e);
    } catch (Exception e) {
      log.error("[AI] Unexpected error while parsing OpenAI response: {}", e.getMessage(), e);
      throw new CustomException(CrewErrorCode.AI_RESPONSE_INVALID, e);
    }
  }

  private void validate(OpenAiDraftResult result) {
    if (result.title() == null || result.title().isBlank()) {
      throw new CustomException(CrewErrorCode.AI_RESPONSE_INVALID);
    }
    if (result.description() == null || result.description().isBlank()) {
      throw new CustomException(CrewErrorCode.AI_RESPONSE_INVALID);
    }
    if (result.frequencyType() == null || !VALID_FREQUENCY_TYPES.contains(result.frequencyType())) {
      throw new CustomException(CrewErrorCode.AI_RESPONSE_INVALID);
    }
    if ("SPECIFIC_DAYS".equals(result.frequencyType())) {
      if (result.missionScheduleDays() == null || result.missionScheduleDays().isEmpty()) {
        throw new CustomException(CrewErrorCode.AI_RESPONSE_INVALID);
      }
      Set<String> seen = new HashSet<>();
      for (String day : result.missionScheduleDays()) {
        if (day == null || day.isBlank()) {
          throw new CustomException(CrewErrorCode.AI_RESPONSE_INVALID);
        }
        try {
          DayOfWeek.valueOf(day);
        } catch (IllegalArgumentException e) {
          throw new CustomException(CrewErrorCode.AI_RESPONSE_INVALID);
        }
        if (!seen.add(day)) {
          throw new CustomException(CrewErrorCode.AI_RESPONSE_INVALID);
        }
      }
    }
    if (result.dailySettlementType() == null
        || !VALID_SETTLEMENT_TYPES.contains(result.dailySettlementType())) {
      throw new CustomException(CrewErrorCode.AI_RESPONSE_INVALID);
    }
    if (result.durationDays() < 1 || result.durationDays() > 365) {
      throw new CustomException(CrewErrorCode.AI_RESPONSE_INVALID);
    }
    if (result.depositAmount() <= 0) {
      throw new CustomException(CrewErrorCode.AI_RESPONSE_INVALID);
    }
  }

  private AiDraft toAiDraft(OpenAiDraftResult result) {
    return new AiDraft(
        result.title(),
        result.description(),
        result.frequencyType(),
        result.missionScheduleDays() != null ? result.missionScheduleDays() : List.of(),
        result.dailySettlementType(),
        result.depositAmount(),
        result.durationDays());
  }
}
