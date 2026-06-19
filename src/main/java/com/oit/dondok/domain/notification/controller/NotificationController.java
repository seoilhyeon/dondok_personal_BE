package com.oit.dondok.domain.notification.controller;

import com.oit.dondok.domain.notification.dto.response.NotificationListResponse;
import com.oit.dondok.domain.notification.dto.response.ReadAllResponse;
import com.oit.dondok.domain.notification.dto.response.UnreadCountResponse;
import com.oit.dondok.domain.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "알림", description = "알림 목록 조회 및 읽음 처리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

  private final NotificationService notificationService;

  @Operation(summary = "알림 목록 조회", description = "내 알림 목록을 커서 기반으로 페이지네이션 조회합니다.")
  @GetMapping
  public ResponseEntity<NotificationListResponse> getNotifications(
      @AuthenticationPrincipal UUID memberUuid,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String cursor) {
    return ResponseEntity.ok(notificationService.findNotifications(memberUuid, limit, cursor));
  }

  @Operation(summary = "읽지 않은 알림 수 조회")
  @GetMapping("/unread-count")
  public ResponseEntity<UnreadCountResponse> getUnreadCount(
      @AuthenticationPrincipal UUID memberUuid) {
    return ResponseEntity.ok(notificationService.getUnreadCount(memberUuid));
  }

  @Operation(summary = "알림 단건 읽음 처리", description = "특정 알림을 읽음 처리합니다.")
  @PatchMapping("/{notificationId}/read")
  public ResponseEntity<Void> read(
      @AuthenticationPrincipal UUID memberUuid, @PathVariable UUID notificationId) {
    notificationService.markAsRead(memberUuid, notificationId);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "알림 전체 읽음 처리", description = "읽지 않은 알림 전체를 읽음 처리합니다.")
  @PatchMapping("/read-all")
  public ResponseEntity<ReadAllResponse> readAll(@AuthenticationPrincipal UUID memberUuid) {
    return ResponseEntity.ok(notificationService.markAllAsRead(memberUuid));
  }
}
