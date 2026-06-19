package com.oit.dondok.domain.notification.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oit.dondok.domain.notification.dto.response.NotificationItemResponse;
import com.oit.dondok.domain.notification.dto.response.NotificationListResponse;
import com.oit.dondok.domain.notification.dto.response.ReadAllResponse;
import com.oit.dondok.domain.notification.exception.NotificationErrorCode;
import com.oit.dondok.domain.notification.service.NotificationService;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class NotificationControllerTest {

  private static final UUID MEMBER_UUID = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");

  @Autowired private MockMvc mockMvc;

  @MockBean private NotificationService notificationService;

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
  void getNotificationsReturnsListWithItems() throws Exception {
    UUID notifUuid = UUID.fromString("018f4fd2-0000-0000-0000-000000000001");
    OffsetDateTime occurredAt = OffsetDateTime.parse("2026-06-10T09:00:00+09:00");
    String nextCursor = encodeCursor(occurredAt, 1L);

    given(notificationService.findNotifications(MEMBER_UUID, null, null))
        .willReturn(
            new NotificationListResponse(
                List.of(
                    new NotificationItemResponse(
                        notifUuid.toString(),
                        "MISSION_LOG_VERIFICATION_RESULT",
                        "mission_log",
                        "9001",
                        "dondok://crews/42/mission-logs/9001",
                        occurredAt,
                        "인증 결과가 반영되었습니다.",
                        null,
                        true,
                        null)),
                nextCursor));

    mockMvc
        .perform(get("/api/notifications"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].notification_id").value(notifUuid.toString()))
        .andExpect(jsonPath("$.items[0].event_type").value("MISSION_LOG_VERIFICATION_RESULT"))
        .andExpect(jsonPath("$.items[0].resource_type").value("mission_log"))
        .andExpect(jsonPath("$.items[0].resource_id").value("9001"))
        .andExpect(jsonPath("$.items[0].deep_link").value("dondok://crews/42/mission-logs/9001"))
        .andExpect(jsonPath("$.items[0].occurred_at").value("2026-06-10T09:00:00+09:00"))
        .andExpect(jsonPath("$.items[0].display_text").value("인증 결과가 반영되었습니다."))
        .andExpect(jsonPath("$.items[0].requires_refetch").value(true))
        .andExpect(jsonPath("$.items[0].read_at").isEmpty())
        .andExpect(jsonPath("$.next_cursor").value(nextCursor));

    then(notificationService).should().findNotifications(MEMBER_UUID, null, null);
  }

  @Test
  void getNotificationsPassesCursorAndLimitToService() throws Exception {
    String cursor = encodeCursor(OffsetDateTime.parse("2026-06-10T10:00:00+09:00"), 5L);

    given(notificationService.findNotifications(MEMBER_UUID, 10, cursor))
        .willReturn(new NotificationListResponse(List.of(), null));

    mockMvc
        .perform(get("/api/notifications").param("limit", "10").param("cursor", cursor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.next_cursor").doesNotExist());

    then(notificationService).should().findNotifications(MEMBER_UUID, 10, cursor);
  }

  @Test
  void readAllReturnsUpdatedCount() throws Exception {
    given(notificationService.markAllAsRead(MEMBER_UUID)).willReturn(new ReadAllResponse(5));

    mockMvc
        .perform(patch("/api/notifications/read-all"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.updated_count").value(5));

    then(notificationService).should().markAllAsRead(MEMBER_UUID);
  }

  @Test
  void readAllReturnsZeroWhenNoUnreadNotifications() throws Exception {
    given(notificationService.markAllAsRead(MEMBER_UUID)).willReturn(new ReadAllResponse(0));

    mockMvc
        .perform(patch("/api/notifications/read-all"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.updated_count").value(0));
  }

  @Test
  void getNotificationsReturnsErrorWhenLimitIsInvalid() throws Exception {
    given(notificationService.findNotifications(MEMBER_UUID, 0, null))
        .willThrow(new CustomException(NotificationErrorCode.INVALID_LIMIT));

    mockMvc
        .perform(get("/api/notifications").param("limit", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_LIMIT"));

    then(notificationService).should().findNotifications(MEMBER_UUID, 0, null);
  }

  @Test
  void getNotificationsReturnsErrorWhenCursorIsInvalid() throws Exception {
    given(notificationService.findNotifications(MEMBER_UUID, null, "bad-cursor"))
        .willThrow(new CustomException(NotificationErrorCode.INVALID_CURSOR));

    mockMvc
        .perform(get("/api/notifications").param("cursor", "bad-cursor"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));

    then(notificationService).should().findNotifications(MEMBER_UUID, null, "bad-cursor");
  }

  private static String encodeCursor(OffsetDateTime occurredAt, long id) {
    String payload = "v1|" + occurredAt + "|" + id;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }
}
