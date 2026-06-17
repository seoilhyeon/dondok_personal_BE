package com.oit.dondok.domain.dashboard.controller;

import com.oit.dondok.domain.dashboard.dto.response.CrewDashboardResponse;
import com.oit.dondok.domain.dashboard.service.CrewDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "대시보드", description = "크루 상세 대시보드 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/crews/{crewId}")
public class CrewDashboardController {

  private final CrewDashboardService crewDashboardService;

  @Operation(
      summary = "크루 상세 대시보드 조회",
      description = "특정 크루의 내 순위/예상 환급금/변동, 참여자별 지분율, 다음 정산 시각을 조회한다.")
  @GetMapping("/dashboard")
  public ResponseEntity<CrewDashboardResponse> getCrewDashboard(
      @PathVariable Long crewId, @AuthenticationPrincipal UUID memberUuid) {
    return ResponseEntity.ok(crewDashboardService.getCrewDashboard(memberUuid, crewId));
  }
}
