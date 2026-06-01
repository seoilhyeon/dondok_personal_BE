package com.oit.dondok.infra.image.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oit.dondok.global.exception.GlobalExceptionHandler;
import com.oit.dondok.infra.image.dto.PresignedUrlRequest;
import com.oit.dondok.infra.image.dto.PresignedUrlResponse;
import com.oit.dondok.infra.image.dto.UploadPurpose;
import com.oit.dondok.infra.image.service.ImageService;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ImageController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ImageControllerTest {

  private static final UUID MEMBER_UUID = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private ImageService imageService;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void getPresignedUrlSuccess() throws Exception {
    // 소유권이 내재적으로 보장되는 PROFILE_IMAGE로 happy path를 검증한다.
    String s3Key = "profile/018f4fd2-6d7a-7a41-9f58-6d07f5c3c901/abc";
    PresignedUrlRequest request =
        new PresignedUrlRequest(UploadPurpose.PROFILE_IMAGE, null, null, "image/jpeg", 2048L);
    given(imageService.generatePresignedUrl(eq(MEMBER_UUID), any(PresignedUrlRequest.class)))
        .willReturn(
            PresignedUrlResponse.of(
                "https://s3.example.com/upload",
                s3Key,
                OffsetDateTime.parse("2026-06-01T12:10:00+09:00")));

    authenticate(MEMBER_UUID);

    mockMvc
        .perform(
            post("/api/uploads/presigned-url")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upload_url").value("https://s3.example.com/upload"))
        .andExpect(jsonPath("$.s3_key").value(s3Key))
        .andExpect(jsonPath("$.expires_at").value("2026-06-01T12:10:00+09:00"));

    // 인증된 사용자의 memberUuid가 서비스로 전달되어 권한 검증의 기준이 되는지 확인한다.
    verify(imageService).generatePresignedUrl(eq(MEMBER_UUID), any(PresignedUrlRequest.class));
  }

  @Test
  void getPresignedUrlRejectsNullPurpose() throws Exception {
    PresignedUrlRequest request = new PresignedUrlRequest(null, 42L, 101L, "image/jpeg", 2048L);

    performAndExpectInvalidInput(request);
  }

  @Test
  void getPresignedUrlRejectsMissionImageWithoutCrewContext() throws Exception {
    // MISSION_IMAGE인데 crew_id / crew_participant_id가 없으면 cross-field 검증에서 막힌다.
    PresignedUrlRequest request =
        new PresignedUrlRequest(UploadPurpose.MISSION_IMAGE, null, null, "image/jpeg", 2048L);

    performAndExpectInvalidInput(request);
  }

  @Test
  void getPresignedUrlRejectsBlankContentType() throws Exception {
    PresignedUrlRequest request =
        new PresignedUrlRequest(UploadPurpose.PROFILE_IMAGE, null, null, " ", 2048L);

    performAndExpectInvalidInput(request);
  }

  @Test
  void getPresignedUrlRejectsNonPositiveContentLength() throws Exception {
    PresignedUrlRequest request =
        new PresignedUrlRequest(UploadPurpose.PROFILE_IMAGE, null, null, "image/jpeg", 0L);

    performAndExpectInvalidInput(request);
  }

  private void performAndExpectInvalidInput(PresignedUrlRequest request) throws Exception {
    mockMvc
        .perform(
            post("/api/uploads/presigned-url")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  private static void authenticate(UUID memberUuid) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(memberUuid, null, Collections.emptyList()));
  }
}
