package com.oit.dondok.domain.member.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullableModule;

class UpdateProfileRequestTest {

  private final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new JsonNullableModule());
  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void omittedFieldIsUndefinedButExplicitNullIsPresent() throws Exception {
    UpdateProfileRequest request =
        objectMapper.readValue("{\"profile_image_s3_key\":null}", UpdateProfileRequest.class);

    assertThat(request.hasAnyIncludedField()).isTrue();
    assertThat(request.includesNickname()).isFalse();
    assertThat(request.includesProfileImageS3Key()).isTrue();
    assertThat(request.profileImageS3KeyValue()).isNull();
    assertThat(request.includesStatusMessage()).isFalse();
  }

  @Test
  void explicitStatusMessageNullIsPresentAsRemoveRequest() throws Exception {
    UpdateProfileRequest request =
        objectMapper.readValue("{\"status_message\":null}", UpdateProfileRequest.class);

    assertThat(request.hasAnyIncludedField()).isTrue();
    assertThat(request.includesNickname()).isFalse();
    assertThat(request.includesProfileImageS3Key()).isFalse();
    assertThat(request.includesStatusMessage()).isTrue();
    assertThat(request.statusMessageValue()).isNull();
  }

  @Test
  void explicitNullRemoveRequestsAreValid() throws Exception {
    UpdateProfileRequest request =
        objectMapper.readValue(
            "{\"profile_image_s3_key\":null,\"status_message\":null}", UpdateProfileRequest.class);

    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void emptyBodyHasNoIncludedFields() throws Exception {
    UpdateProfileRequest request = objectMapper.readValue("{}", UpdateProfileRequest.class);

    assertThat(request.hasAnyIncludedField()).isFalse();
    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void nicknameMustBeAtLeastTwoCharacters() throws Exception {
    UpdateProfileRequest request =
        objectMapper.readValue("{\"nickname\":\"a\"}", UpdateProfileRequest.class);

    assertViolationMessage(request, "nickname은 2자 이상 10자 이하여야 합니다.");
  }

  @Test
  void nicknameMustBeAtMostTenCharacters() throws Exception {
    UpdateProfileRequest request =
        objectMapper.readValue("{\"nickname\":\"12345678901\"}", UpdateProfileRequest.class);

    assertViolationMessage(request, "nickname은 2자 이상 10자 이하여야 합니다.");
  }

  @Test
  void nicknameMustNotBeBlank() throws Exception {
    UpdateProfileRequest request =
        objectMapper.readValue("{\"nickname\":\"   \"}", UpdateProfileRequest.class);

    assertViolationMessage(request, "nickname은 필수입니다.");
  }

  @Test
  void nicknameIsTrimmedBeforeValidation() throws Exception {
    UpdateProfileRequest request =
        objectMapper.readValue("{\"nickname\":\" abc \"}", UpdateProfileRequest.class);

    assertThat(request.nicknameValue()).isEqualTo("abc");
    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void statusMessageMustBeAtMostOneHundredCharactersWhenPresent() throws Exception {
    String statusMessage = "a".repeat(101);
    UpdateProfileRequest request =
        objectMapper.readValue(
            "{\"status_message\":\"" + statusMessage + "\"}", UpdateProfileRequest.class);

    assertViolationMessage(request, "status_message는 100자 이하여야 합니다.");
  }

  private void assertViolationMessage(UpdateProfileRequest request, String message) {
    Set<ConstraintViolation<UpdateProfileRequest>> violations = validator.validate(request);

    assertThat(violations).extracting(ConstraintViolation::getMessage).contains(message);
  }
}
