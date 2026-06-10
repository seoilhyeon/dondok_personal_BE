package com.oit.dondok.domain.mission.controller;

import com.oit.dondok.domain.mission.dto.response.HostMissionLogReviewListResponse;
import com.oit.dondok.domain.mission.service.HostMissionLogReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "방장 미션 인증 검토", description = "방장 미션 인증 검토 목록 API")
@RestController
@RequestMapping("/api/crews/{crewId}/host/mission-logs")
@RequiredArgsConstructor
public class HostMissionLogReviewController {

  private final HostMissionLogReviewService hostMissionLogReviewService;

  // 방장이 검토 가능한 인증 목록을 bucket별 무한 스크롤 방식으로 조회한다.
  @Operation(
      summary = "검토 가능 인증 목록 조회",
      description = "방장이 수동 검토할 수 있는 미션 인증 목록과 bucket별 카운트를 조회합니다.")
  @GetMapping("/reviewable")
  public ResponseEntity<HostMissionLogReviewListResponse> getReviewableMissionLogs(
      @AuthenticationPrincipal UUID memberUuid,
      @PathVariable Long crewId,
      @RequestParam String bucket,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return ResponseEntity.ok(
        hostMissionLogReviewService.getReviewableMissionLogs(
            memberUuid, crewId, bucket, cursor, limit));
  }
}
