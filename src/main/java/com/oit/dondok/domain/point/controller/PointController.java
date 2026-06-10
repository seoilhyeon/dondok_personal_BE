package com.oit.dondok.domain.point.controller;

import com.oit.dondok.domain.point.dto.response.PointBalanceResponse;
import com.oit.dondok.domain.point.dto.response.PointHistoryListResponse;
import com.oit.dondok.domain.point.dto.response.WalletHistoryListResponse;
import com.oit.dondok.domain.point.service.PointQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "포인트", description = "포인트 지갑/내역 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/points")
public class PointController {

  private final PointQueryService pointQueryService;

  @Operation(summary = "포인트 잔액 조회", description = "현재 포인트 잔액과 지갑 요약 정보를 조회합니다.")
  @GetMapping
  public ResponseEntity<PointBalanceResponse> getBalance(@AuthenticationPrincipal UUID memberUuid) {
    return ResponseEntity.ok(pointQueryService.findBalance(memberUuid));
  }

  @Operation(summary = "포인트 내역 조회", description = "포인트 내역을 유형/월 필터와 커서 페이지네이션으로 조회합니다.")
  @GetMapping("/history")
  public ResponseEntity<PointHistoryListResponse> getHistories(
      @AuthenticationPrincipal UUID memberUuid,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String month) {
    return ResponseEntity.ok(
        pointQueryService.findHistories(memberUuid, limit, cursor, type, month));
  }

  @Operation(summary = "지갑 표시용 포인트 내역 조회", description = "지갑 화면에 표시할 포인트 이벤트 내역을 조회합니다.")
  @GetMapping("/wallet-history")
  public ResponseEntity<WalletHistoryListResponse> getWalletHistories(
      @AuthenticationPrincipal UUID memberUuid,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String month) {
    return ResponseEntity.ok(
        pointQueryService.findWalletHistories(memberUuid, limit, cursor, type, month));
  }
}
