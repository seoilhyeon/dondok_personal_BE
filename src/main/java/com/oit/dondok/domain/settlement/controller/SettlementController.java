package com.oit.dondok.domain.settlement.controller;

import com.oit.dondok.domain.settlement.dto.response.SettlementDetailResponse;
import com.oit.dondok.domain.settlement.dto.response.SettlementSummaryResponse;
import com.oit.dondok.domain.settlement.service.SettlementQueryService;
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

@Tag(name = "정산", description = "정산 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class SettlementController {

  private final SettlementQueryService settlementQueryService;

  @Operation(summary = "크루 정산 상태 조회", description = "특정 크루의 정산 상태를 조회한다.")
  @GetMapping("/crews/{crewId}/settlement")
  public ResponseEntity<SettlementSummaryResponse> getCrewSettlement(
      @AuthenticationPrincipal UUID memberUuid, @PathVariable Long crewId) {
    return ResponseEntity.ok(settlementQueryService.getSettlementSummary(crewId, memberUuid));
  }

  @Operation(summary = "정산 결과 상세 조회", description = "정산 결과의 상세 항목과 환급 내역을 조회한다.")
  @GetMapping("/settlements/{settlementId}")
  public ResponseEntity<SettlementDetailResponse> getSettlement(
      @AuthenticationPrincipal UUID memberUuid, @PathVariable Long settlementId) {
    return ResponseEntity.ok(settlementQueryService.getSettlementDetail(settlementId, memberUuid));
  }
}
