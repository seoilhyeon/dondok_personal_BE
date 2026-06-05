package com.oit.dondok.domain.mission.controller;

import com.oit.dondok.domain.mission.dto.request.MissionLogCreateRequest;
import com.oit.dondok.domain.mission.dto.response.MissionLogCreateResponse;
import com.oit.dondok.domain.mission.service.MissionLogService;
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

@Tag(name = "미션 인증", description = "미션 인증 로그 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mission-logs")
public class MissionLogController {

  private final MissionLogService missionLogService;

  @Operation(
      summary = "미션 인증 로그 생성",
      description = "업로드된 이미지로 미션 인증 로그를 생성합니다. 제출 직후 상태는 PENDING_REVIEW입니다.")
  @PostMapping
  public ResponseEntity<MissionLogCreateResponse> createMissionLog(
      @AuthenticationPrincipal UUID memberUuid,
      @Valid @RequestBody MissionLogCreateRequest request) {
    MissionLogCreateResponse response = missionLogService.createMissionLog(memberUuid, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
