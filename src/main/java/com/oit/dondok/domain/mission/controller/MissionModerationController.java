package com.oit.dondok.domain.mission.controller;

import com.oit.dondok.domain.mission.dto.response.MissionModerationResponse;
import com.oit.dondok.domain.mission.service.MissionModerationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mission-logs/{missionLogId}/moderation")
@RequiredArgsConstructor
public class MissionModerationController {

  private final MissionModerationService missionModerationService;

  // 방장이 검수 대기 중인 미션 인증을 승인
  @PostMapping("/approve")
  public ResponseEntity<MissionModerationResponse> approve(
      @AuthenticationPrincipal UUID memberUuid, @PathVariable Long missionLogId) {
    return ResponseEntity.ok(missionModerationService.approve(memberUuid, missionLogId));
  }
}
