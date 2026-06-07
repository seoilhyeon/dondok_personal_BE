package com.oit.dondok.infra.ai.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.port.AiDraft;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.infra.ai.config.OpenAiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

class OpenAiRecommendationAdapterTest {

  private final RestTemplate restTemplate = mock(RestTemplate.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final OpenAiProperties props = new OpenAiProperties("test-api-key", "gpt-4.1-mini");

  private OpenAiRecommendationAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new OpenAiRecommendationAdapter(restTemplate, objectMapper, props);
  }

  private String openAiResponse(String content) throws Exception {
    return String.format(
        "{\"choices\":[{\"message\":{\"content\":%s}}]}", objectMapper.writeValueAsString(content));
  }

  @Test
  void requestDraftReturnsAiDraftOnValidResponse() throws Exception {
    String content =
        "{\"title\":\"아침 독서\",\"description\":\"매일 독서 인증\","
            + "\"frequency_type\":\"DAILY\",\"mission_schedule_days\":[],"
            + "\"daily_settlement_type\":\"A\",\"deposit_amount\":10000,\"duration_days\":30}";
    given(restTemplate.postForObject(anyString(), any(), eq(String.class)))
        .willReturn(openAiResponse(content));

    AiDraft result = adapter.requestDraft("독서 습관");

    assertThat(result.title()).isEqualTo("아침 독서");
    assertThat(result.depositAmount()).isEqualTo(10_000L);
    assertThat(result.durationDays()).isEqualTo(30);
    assertThat(result.frequencyType()).isEqualTo("DAILY");
  }

  @Test
  void requestDraftThrowsAiRecommendationFailedWhenApiKeyIsBlank() {
    OpenAiProperties emptyKeyProps = new OpenAiProperties("", "gpt-4.1-mini");
    OpenAiRecommendationAdapter adapterWithEmptyKey =
        new OpenAiRecommendationAdapter(restTemplate, objectMapper, emptyKeyProps);

    assertThatThrownBy(() -> adapterWithEmptyKey.requestDraft("독서 습관"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.AI_RECOMMENDATION_FAILED);
  }

  @Test
  void requestDraftThrowsAiRecommendationFailedWhenRestClientFails() {
    given(restTemplate.postForObject(anyString(), any(), eq(String.class)))
        .willThrow(new RestClientException("connection refused"));

    assertThatThrownBy(() -> adapter.requestDraft("독서 습관"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.AI_RECOMMENDATION_FAILED);
  }

  @Test
  void requestDraftThrowsAiResponseInvalidOnEmptyJsonContent() throws Exception {
    given(restTemplate.postForObject(anyString(), any(), eq(String.class)))
        .willReturn(openAiResponse("{}"));

    assertThatThrownBy(() -> adapter.requestDraft("독서 습관"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.AI_RESPONSE_INVALID);
  }

  @Test
  void requestDraftThrowsAiResponseInvalidOnUnknownFrequencyType() throws Exception {
    String content =
        "{\"title\":\"독서\",\"description\":\"매일 독서\","
            + "\"frequency_type\":\"WEEKLY\",\"mission_schedule_days\":[],"
            + "\"daily_settlement_type\":\"A\",\"deposit_amount\":10000,\"duration_days\":30}";
    given(restTemplate.postForObject(anyString(), any(), eq(String.class)))
        .willReturn(openAiResponse(content));

    assertThatThrownBy(() -> adapter.requestDraft("독서 습관"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.AI_RESPONSE_INVALID);
  }

  @Test
  void requestDraftThrowsAiResponseInvalidWhenSpecificDaysHasEmptyScheduleDays() throws Exception {
    String content =
        "{\"title\":\"독서\",\"description\":\"매일 독서\","
            + "\"frequency_type\":\"SPECIFIC_DAYS\",\"mission_schedule_days\":[],"
            + "\"daily_settlement_type\":\"A\",\"deposit_amount\":10000,\"duration_days\":30}";
    given(restTemplate.postForObject(anyString(), any(), eq(String.class)))
        .willReturn(openAiResponse(content));

    assertThatThrownBy(() -> adapter.requestDraft("독서 습관"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.AI_RESPONSE_INVALID);
  }

  @Test
  void requestDraftThrowsAiResponseInvalidWhenDurationDaysIsOutOfRange() throws Exception {
    String content =
        "{\"title\":\"독서\",\"description\":\"매일 독서\","
            + "\"frequency_type\":\"DAILY\",\"mission_schedule_days\":[],"
            + "\"daily_settlement_type\":\"A\",\"deposit_amount\":10000,\"duration_days\":400}";
    given(restTemplate.postForObject(anyString(), any(), eq(String.class)))
        .willReturn(openAiResponse(content));

    assertThatThrownBy(() -> adapter.requestDraft("독서 습관"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.AI_RESPONSE_INVALID);
  }
}
