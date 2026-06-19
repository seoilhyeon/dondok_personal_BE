package com.oit.dondok.domain.notification.controller;

import com.oit.dondok.domain.notification.dto.request.RegisterDeviceRequest;
import com.oit.dondok.domain.notification.dto.response.RegisterDeviceResponse;
import com.oit.dondok.domain.notification.service.NotificationDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "알림 디바이스", description = "FCM 디바이스 등록 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications/devices")
public class NotificationDeviceController {

  private final NotificationDeviceService notificationDeviceService;

  @Operation(
      summary = "FCM 디바이스 등록",
      description = "FCM 토큰을 등록합니다. 동일 device_id가 이미 존재하면 토큰을 갱신합니다.")
  @PostMapping
  public ResponseEntity<RegisterDeviceResponse> registerDevice(
      @AuthenticationPrincipal UUID memberUuid, @Valid @RequestBody RegisterDeviceRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(notificationDeviceService.registerDevice(memberUuid, request));
  }
}
