package com.oit.dondok.domain.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oit.dondok.domain.notification.dto.request.NotificationSettingsRequest;
import com.oit.dondok.domain.notification.dto.response.NotificationSettingsResponse;
import com.oit.dondok.domain.notification.entity.NotificationCategory;
import com.oit.dondok.domain.notification.exception.NotificationErrorCode;
import com.oit.dondok.domain.notification.service.NotificationSettingsService;
import com.oit.dondok.global.config.JsonNullableConfig;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalExceptionHandler;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationSettingsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, JsonNullableConfig.class})
class NotificationSettingsControllerTest {

  private static final UUID MEMBER_UUID = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private NotificationSettingsService notificationSettingsService;

  @BeforeEach
  void setUpAuthentication() {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(MEMBER_UUID, null, List.of()));
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void getSettingsReturns200WithDefaults() throws Exception {
    given(notificationSettingsService.getSettings(MEMBER_UUID))
        .willReturn(NotificationSettingsResponse.defaults());

    mockMvc
        .perform(get("/api/notification-settings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories.EMOJI_REACTION").value(true))
        .andExpect(jsonPath("$.categories.SETTLEMENT").value(true))
        .andExpect(jsonPath("$.quiet_start_time").doesNotExist());
  }

  @Test
  void patchSettingsReturns200WithUpdatedValues() throws Exception {
    NotificationSettingsResponse response =
        new NotificationSettingsResponse(
            Map.of(
                NotificationCategory.EMOJI_REACTION, false,
                NotificationCategory.HOST_VERIFICATION, true,
                NotificationCategory.DEADLINE_APPROACHING, true,
                NotificationCategory.DAILY_RESULT, true,
                NotificationCategory.SETTLEMENT, true,
                NotificationCategory.CREW_DISBANDED, true,
                NotificationCategory.CREW_NEWS, true),
            "22:00",
            "07:00");

    given(
            notificationSettingsService.saveSettings(
                eq(MEMBER_UUID), any(NotificationSettingsRequest.class)))
        .willReturn(response);

    NotificationSettingsRequest request =
        new NotificationSettingsRequest(
            Map.of(NotificationCategory.EMOJI_REACTION, false), time("22:00"), time("07:00"));

    mockMvc
        .perform(
            patch("/api/notification-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories.EMOJI_REACTION").value(false))
        .andExpect(jsonPath("$.quiet_start_time").value("22:00"))
        .andExpect(jsonPath("$.quiet_end_time").value("07:00"));
  }

  @Test
  void patchSettingsReturns400WhenOnlyStartTimeProvided() throws Exception {
    given(
            notificationSettingsService.saveSettings(
                eq(MEMBER_UUID), any(NotificationSettingsRequest.class)))
        .willThrow(new CustomException(NotificationErrorCode.INVALID_QUIET_HOURS));

    NotificationSettingsRequest request =
        new NotificationSettingsRequest(null, time("22:00"), omittedTime());

    mockMvc
        .perform(
            patch("/api/notification-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_QUIET_HOURS"));
  }

  @Test
  void patchSettingsReturns400WhenCategoryValueIsNull() throws Exception {
    mockMvc
        .perform(
            patch("/api/notification-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categories\":{\"EMOJI_REACTION\":null}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

    then(notificationSettingsService).shouldHaveNoInteractions();
  }

  private static JsonNullable<String> time(String value) {
    return JsonNullable.of(value);
  }

  private static JsonNullable<String> omittedTime() {
    return JsonNullable.undefined();
  }
}
