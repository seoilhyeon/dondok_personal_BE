package com.oit.dondok.domain.dashboard.controller;

import com.oit.dondok.domain.dashboard.dto.response.DashboardResponse;
import com.oit.dondok.domain.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.ServletResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "대시보드", description = "전체 대시보드 집계 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dashboard")
public class DashboardController {

  private final DashboardService dashboardService;

  @Operation(
      summary = "전체 대시보드 집계 조희",
      description = "내가 참여 중인 전체 크루의 예상 환급금 합계, 오늘 변동액, 크루별 현황을 조회한다.")
  @GetMapping
  public ResponseEntity<DashboardResponse> getDashboard(
      @AuthenticationPrincipal UUID memberUuid, ServletResponse servletResponse) {
    return ResponseEntity.ok(dashboardService.getDashboard(memberUuid));
  }
}
