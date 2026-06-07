package com.oit.dondok.domain.crew.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.oit.dondok.domain.crew.dto.request.AiRecommendationRequest;
import com.oit.dondok.domain.crew.dto.response.AiRecommendationResponse;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.port.AiDraft;
import com.oit.dondok.domain.crew.port.AiRecommendationPort;
import com.oit.dondok.global.exception.CustomException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AiRecommendationServiceTest {

  @Mock private AiRecommendationPort aiRecommendationPort;
  @InjectMocks private AiRecommendationService aiRecommendationService;

  private AiDraft buildDraft(long depositAmount) {
    return new AiDraft("아침 독서", "매일 아침 독서 인증", "DAILY", List.of(), "A", depositAmount, 30);
  }

  @Test
  void getRecommendationReturnsNormalDraftWithNoWarningsWhenDepositIsMultipleOf1000() {
    given(aiRecommendationPort.requestDraft("독서 습관")).willReturn(buildDraft(10_000L));

    AiRecommendationResponse result =
        aiRecommendationService.getRecommendation(new AiRecommendationRequest("독서 습관"));

    assertThat(result.draft().depositAmount()).isEqualTo(10_000L);
    assertThat(result.validationWarnings()).isEmpty();
  }

  @Test
  void getRecommendationRoundsDepositUpToNearest1000AndAddsWarning() {
    given(aiRecommendationPort.requestDraft("독서 습관")).willReturn(buildDraft(1_500L));

    AiRecommendationResponse result =
        aiRecommendationService.getRecommendation(new AiRecommendationRequest("독서 습관"));

    assertThat(result.draft().depositAmount()).isEqualTo(2_000L);
    assertThat(result.validationWarnings()).hasSize(1);
    assertThat(result.validationWarnings().get(0).field()).isEqualTo("deposit_amount");
    assertThat(result.validationWarnings().get(0).message()).contains("1,000원 단위");
  }

  @Test
  void getRecommendationClampsDepositToMaxRangeAndAddsWarning() {
    given(aiRecommendationPort.requestDraft("독서 습관")).willReturn(buildDraft(200_000L));

    AiRecommendationResponse result =
        aiRecommendationService.getRecommendation(new AiRecommendationRequest("독서 습관"));

    assertThat(result.draft().depositAmount()).isEqualTo(100_000L);
    assertThat(result.validationWarnings()).hasSize(1);
    assertThat(result.validationWarnings().get(0).field()).isEqualTo("deposit_amount");
    assertThat(result.validationWarnings().get(0).message()).contains("허용 범위");
  }

  @Test
  void getRecommendationPropagatesAiRecommendationFailedWhenPortFails() {
    given(aiRecommendationPort.requestDraft(any()))
        .willThrow(new CustomException(CrewErrorCode.AI_RECOMMENDATION_FAILED));

    assertThatThrownBy(
            () -> aiRecommendationService.getRecommendation(new AiRecommendationRequest("독서 습관")))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.AI_RECOMMENDATION_FAILED);
  }

  @Test
  void getRecommendationPropagatesAiResponseInvalidWhenPortReturnsInvalidResponse() {
    given(aiRecommendationPort.requestDraft(any()))
        .willThrow(new CustomException(CrewErrorCode.AI_RESPONSE_INVALID));

    assertThatThrownBy(
            () -> aiRecommendationService.getRecommendation(new AiRecommendationRequest("독서 습관")))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.AI_RESPONSE_INVALID);
  }

  @Test
  void aiRecommendationFailedMapsToHttpBadGateway() {
    assertThat(CrewErrorCode.AI_RECOMMENDATION_FAILED.getStatus())
        .isEqualTo(HttpStatus.BAD_GATEWAY);
  }

  @Test
  void aiResponseInvalidMapsToHttpUnprocessableEntity() {
    assertThat(CrewErrorCode.AI_RESPONSE_INVALID.getStatus())
        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }
}
