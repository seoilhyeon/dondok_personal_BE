package com.oit.dondok.infra.loadtest.controller;

import com.oit.dondok.infra.loadtest.service.LoadTestFixtureService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("load-test & !prod")
@RequestMapping("/api/load-test")
@RequiredArgsConstructor
public class LoadTestFixtureController {
  private final LoadTestFixtureService fixtureService;

  @PostMapping("/seed")
  public Map<String, String> seed() {
    return fixtureService.seed();
  }

  @PostMapping("/reset")
  public Map<String, String> reset() {
    return fixtureService.reset();
  }

  @PostMapping("/point-charge")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void pointCharge(@RequestParam String paymentId, @RequestParam String orderId) {
    fixtureService.pointCharge(paymentId, orderId);
  }

  @PostMapping("/recovery")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void recovery() {
    fixtureService.recovery();
  }

  @PostMapping("/settlement/final")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void finalBatch() {
    fixtureService.finalBatch();
  }

  @PostMapping("/settlement/retry")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void retryBatch() {
    fixtureService.retryBatch();
  }

  @PostMapping("/runs/point/prepare")
  public LoadTestFixtureService.PreparedRun preparePointRun(
      @Valid @RequestBody PointPrepareRequest request) {
    return fixtureService.preparePointRun(request.runId(), request.accounts());
  }

  @PostMapping("/runs/point/tokens")
  public LoadTestFixtureService.PointTokens pointTokens(@Valid @RequestBody RunRequest request) {
    return fixtureService.pointTokens(request.runId());
  }

  @PostMapping("/runs/settlement/final/preflight")
  public ResponseEntity<LoadTestFixtureService.SettlementPreflight> preflightFinalSettlements(
      @Valid @RequestBody RunRequest request) {
    LoadTestFixtureService.SettlementPreflight response =
        fixtureService.preflightFinalSettlements();
    return ResponseEntity.status(response.safe() ? HttpStatus.OK : HttpStatus.CONFLICT)
        .body(response);
  }

  @PostMapping("/runs/settlement/final/prepare")
  public LoadTestFixtureService.PreparedRun prepareFinalSettlementRun(
      @Valid @RequestBody SettlementPrepareRequest request) {
    return fixtureService.prepareFinalSettlementRun(request.runId(), request.settlements());
  }

  @PostMapping("/runs/settlement/final/trigger")
  public LoadTestFixtureService.SettlementRunResult triggerFinalSettlementRun(
      @Valid @RequestBody RunRequest request) {
    return fixtureService.triggerFinalSettlementRun(request.runId());
  }

  @ExceptionHandler(LoadTestFixtureService.UnsafeSettlementPreflightException.class)
  public ResponseEntity<LoadTestFixtureService.SettlementPreflight> unsafeSettlementPreflight(
      LoadTestFixtureService.UnsafeSettlementPreflightException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.preflight());
  }

  public record RunRequest(@NotBlank @Pattern(regexp = "[a-z0-9-]{1,32}") String runId) {}

  public record PointPrepareRequest(
      @NotBlank @Pattern(regexp = "[a-z0-9-]{1,32}") String runId,
      @Min(1) @Max(200) int accounts) {}

  public record SettlementPrepareRequest(
      @NotBlank @Pattern(regexp = "[a-z0-9-]{1,32}") String runId,
      @Min(1) @Max(1_000) int settlements) {}
}
