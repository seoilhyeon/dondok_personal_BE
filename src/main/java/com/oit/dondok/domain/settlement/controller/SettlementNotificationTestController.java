package com.oit.dondok.domain.settlement.controller;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.settlement.service.SettlementNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Profile({"local", "test"})
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/test/settlements")
public class SettlementNotificationTestController {

  private final SettlementNotificationService settlementNotificationService;

  @PostMapping("/{settlementId}/completed-notifications")
  public ResponseEntity<SettlementCompletedNotificationTestResponse>
      resendSettlementCompletedNotifications(@PathVariable Long settlementId) {
    try {
      int sentItemCount =
          settlementNotificationService.resendSettlementCompletedNotifications(settlementId);
      return ResponseEntity.ok(
          new SettlementCompletedNotificationTestResponse(settlementId, sentItemCount));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record SettlementCompletedNotificationTestResponse(Long settlementId, int sentItemCount) {}
}
