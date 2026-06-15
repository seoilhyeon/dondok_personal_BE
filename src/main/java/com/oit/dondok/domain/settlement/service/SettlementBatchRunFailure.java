package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.settlement.entity.SettlementFailureCode;
import lombok.Getter;

@Getter
class SettlementBatchRunFailure extends RuntimeException {

  private final SettlementFailureCode failureCode;

  SettlementBatchRunFailure(SettlementFailureCode failureCode, String message, Throwable cause) {
    super(message, cause);
    this.failureCode = failureCode;
  }

  SettlementBatchRunFailure(SettlementFailureCode failureCode, String message) {
    super(message);
    this.failureCode = failureCode;
  }
}
