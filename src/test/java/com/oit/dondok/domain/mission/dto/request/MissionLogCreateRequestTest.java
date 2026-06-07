package com.oit.dondok.domain.mission.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MissionLogCreateRequestTest {

  private static final Long CREW_ID = 42L;
  private static final String S3_KEY = "mission/42/101/3f2504e0-4f89-41d3-9a0c-0305e82c3301";
  private static final String CAPTION = "오늘도 미션 완료";

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void validRequestHasNoViolations() {
    MissionLogCreateRequest request = new MissionLogCreateRequest(CREW_ID, S3_KEY, CAPTION);

    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void crewIdMustNotBeNull() {
    MissionLogCreateRequest request = new MissionLogCreateRequest(null, S3_KEY, CAPTION);

    assertViolationMessage(request, "crew_id는 필수입니다.");
  }

  @Test
  void imageS3KeyMustNotBeNull() {
    MissionLogCreateRequest request = new MissionLogCreateRequest(CREW_ID, null, CAPTION);

    assertViolationMessage(request, "image_s3_key는 필수입니다.");
  }

  @Test
  void imageS3KeyMustNotBeBlank() {
    MissionLogCreateRequest request = new MissionLogCreateRequest(CREW_ID, "   ", CAPTION);

    assertViolationMessage(request, "image_s3_key는 필수입니다.");
  }

  @Test
  void captionMustNotBeNull() {
    MissionLogCreateRequest request = new MissionLogCreateRequest(CREW_ID, S3_KEY, null);

    assertViolationMessage(request, "caption은 필수입니다.");
  }

  @Test
  void captionMustNotBeBlank() {
    MissionLogCreateRequest request = new MissionLogCreateRequest(CREW_ID, S3_KEY, "     ");

    assertViolationMessage(request, "caption은 필수입니다.");
  }

  @Test
  void captionMustBeAtLeastFiveCharacters() {
    MissionLogCreateRequest request = new MissionLogCreateRequest(CREW_ID, S3_KEY, "네글자임");

    assertViolationMessage(request, "caption은 5자 이상 100자 이하여야 합니다.");
  }

  @Test
  void captionMustBeAtMostOneHundredCharacters() {
    MissionLogCreateRequest request = new MissionLogCreateRequest(CREW_ID, S3_KEY, "가".repeat(101));

    assertViolationMessage(request, "caption은 5자 이상 100자 이하여야 합니다.");
  }

  private void assertViolationMessage(MissionLogCreateRequest request, String message) {
    Set<ConstraintViolation<MissionLogCreateRequest>> violations = validator.validate(request);

    assertThat(violations).extracting(ConstraintViolation::getMessage).contains(message);
  }
}
