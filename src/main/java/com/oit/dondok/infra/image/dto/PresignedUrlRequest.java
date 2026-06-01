package com.oit.dondok.infra.image.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PresignedUrlRequest(
    @NotNull(message = "purpose는 필수입니다.") UploadPurpose purpose,
    @Positive(message = "crew_id는 양수여야 합니다.") Long crewId,
    @Positive(message = "crew_participant_id는 양수여야 합니다.") Long crewParticipantId,
    @NotBlank(message = "content_type은 필수입니다.") String contentType,
    @NotNull(message = "content_length는 필수입니다.") @Positive(message = "content_length는 양수여야 합니다.")
        Long contentLength) {

  // MISSION_IMAGE는 mission/{crewId}/{crewParticipantId} namespace에 업로드하므로 두 식별자가 반드시 필요하다.
  // PROFILE_IMAGE/CREW_IMAGE는 발급 요청자의 memberUuid namespace를 사용하므로 crew 식별자가 필요 없다.
  @JsonIgnore
  @AssertTrue(message = "MISSION_IMAGE는 crew_id와 crew_participant_id가 필요합니다.")
  public boolean isCrewContextValid() {
    if (purpose != UploadPurpose.MISSION_IMAGE) {
      return true;
    }
    return crewId != null && crewParticipantId != null;
  }
}
