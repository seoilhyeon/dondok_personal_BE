package com.oit.dondok.global.health.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "헬스 체크", description = "서버 상태 확인 API")
public class HealthCheckController {

  @Operation(summary = "서버 상태 확인", description = "서버가 정상적으로 동작 중인지 확인합니다.")
  @GetMapping("/health")
  public ResponseEntity<Map<String, String>> health() {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("status", "UP"));
  }
}
