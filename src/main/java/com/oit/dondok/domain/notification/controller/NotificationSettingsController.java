package com.oit.dondok.domain.notification.controller;

import com.oit.dondok.domain.notification.dto.request.NotificationSettingsRequest;
import com.oit.dondok.domain.notification.dto.response.NotificationSettingsResponse;
import com.oit.dondok.domain.notification.service.NotificationSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "알림 설정", description = "카테고리별 알림 토글 및 방해금지 시간 설정 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notification-settings")
public class NotificationSettingsController {

  private final NotificationSettingsService notificationSettingsService;

  @Operation(summary = "알림 설정 조회", description = "설정이 없으면 기본값(전체 허용)을 반환합니다.")
  @GetMapping
  public ResponseEntity<NotificationSettingsResponse> getSettings(
      @AuthenticationPrincipal UUID memberUuid) {
    return ResponseEntity.ok(notificationSettingsService.getSettings(memberUuid));
  }

  @Operation(summary = "알림 설정 저장", description = "카테고리 토글과 방해금지 시간을 저장합니다.")
  @PatchMapping
  public ResponseEntity<NotificationSettingsResponse> saveSettings(
      @AuthenticationPrincipal UUID memberUuid,
      @Valid @RequestBody NotificationSettingsRequest request) {
    return ResponseEntity.ok(notificationSettingsService.saveSettings(memberUuid, request));
  }
}
