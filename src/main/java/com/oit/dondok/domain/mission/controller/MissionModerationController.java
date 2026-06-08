package com.oit.dondok.domain.mission.controller;

import com.oit.dondok.domain.mission.dto.response.MissionModerationResponse;
import com.oit.dondok.domain.mission.service.MissionModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "미션 검토", description = "방장 미션 인증 검토 관련 API")
@RestController
@RequestMapping("/api/mission-logs/{missionLogId}/moderation")
@RequiredArgsConstructor
public class MissionModerationController {

  private final MissionModerationService missionModerationService;

  // 방장이 검토 대기 중인 미션 인증을 승인
  @Operation(
      summary = "미션 인증 승인",
      description =
          "방장이 검토 대기 중인 미션 인증을 승인합니다. 승인 시 인증 상태가 SUCCESS로 변경되고 MANUAL_APPROVE 검토 이력이 추가됩니다.")
  @PostMapping("/approve")
  public ResponseEntity<MissionModerationResponse> approve(
      @AuthenticationPrincipal UUID memberUuid, @PathVariable Long missionLogId) {
    return ResponseEntity.ok(missionModerationService.approve(memberUuid, missionLogId));
  }
}
