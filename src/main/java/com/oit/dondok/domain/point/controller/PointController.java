package com.oit.dondok.domain.point.controller;

import com.oit.dondok.domain.point.dto.request.PointChargeRequest;
import com.oit.dondok.domain.point.dto.response.PointBalanceResponse;
import com.oit.dondok.domain.point.dto.response.PointChargeResponse;
import com.oit.dondok.domain.point.dto.response.PointHistoryListResponse;
import com.oit.dondok.domain.point.dto.response.WalletHistoryListResponse;
import com.oit.dondok.domain.point.service.PointChargeResult;
import com.oit.dondok.domain.point.service.PointChargeService;
import com.oit.dondok.domain.point.service.PointQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "포인트", description = "포인트 잔액/내역 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/points")
public class PointController {

  private final PointQueryService pointQueryService;
  private final PointChargeService pointChargeService;

  @Operation(summary = "포인트 잔액 조회", description = "현재 사용자의 포인트 잔액 상세를 조회합니다.")
  @GetMapping
  public ResponseEntity<PointBalanceResponse> getBalance(@AuthenticationPrincipal UUID memberUuid) {
    return ResponseEntity.ok(pointQueryService.findBalance(memberUuid));
  }

  @Operation(summary = "포인트 충전", description = "TossPayments 결제를 승인 확인 후 포인트를 충전합니다.")
  @PostMapping("/charges")
  public ResponseEntity<PointChargeResponse> charge(
      @AuthenticationPrincipal UUID memberUuid, @Valid @RequestBody PointChargeRequest request) {
    PointChargeResult result = pointChargeService.charge(memberUuid, request);
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(result.response());
  }

  @Operation(summary = "포인트 원장 내역 조회", description = "포인트 입출금 거래 이력을 페이지네이션으로 조회합니다.")
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

  @Operation(summary = "지갑 표시용 포인트 내역 조회", description = "지갑 화면에 표시할 사용자 관점의 포인트 이벤트 내역을 조회합니다.")
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
