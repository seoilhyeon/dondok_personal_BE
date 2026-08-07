package com.oit.dondok.infra.loadtest.controller;

import com.oit.dondok.infra.loadtest.service.LoadTestFixtureService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
  public HttpStatus pointCharge(@RequestParam String paymentId, @RequestParam String orderId) {
    return fixtureService.pointCharge(paymentId, orderId);
  }

  @PostMapping("/recovery")
  public HttpStatus recovery() {
    return fixtureService.recovery();
  }

  @PostMapping("/settlement/final")
  public HttpStatus finalBatch() {
    return fixtureService.finalBatch();
  }

  @PostMapping("/settlement/retry")
  public HttpStatus retryBatch() {
    return fixtureService.retryBatch();
  }
}
