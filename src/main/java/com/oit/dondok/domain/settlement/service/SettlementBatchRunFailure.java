package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.settlement.entity.SettlementFailureCode;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.ErrorCode;
import com.oit.dondok.global.exception.GlobalErrorCode;
import java.util.Objects;
import lombok.Getter;

@Getter
class SettlementBatchRunFailure extends CustomException {

  private final SettlementFailureCode failureCode;
  private final String message;

  SettlementBatchRunFailure(SettlementFailureCode failureCode, String message, Throwable cause) {
    super(mapToErrorCode(failureCode), cause);
    this.failureCode = failureCode;
    this.message = message;
  }

  SettlementBatchRunFailure(SettlementFailureCode failureCode, String message) {
    super(mapToErrorCode(failureCode));
    this.failureCode = failureCode;
    this.message = message;
  }

  @Override
  public String getMessage() {
    return message;
  }

  private static ErrorCode mapToErrorCode(SettlementFailureCode failureCode) {
    Objects.requireNonNull(failureCode, "failureCode must not be null");

    return switch (failureCode) {
      case INPUT_LOAD_FAILED -> GlobalErrorCode.INVALID_INPUT;
      case DUPLICATE_SETTLEMENT,
          LOCK_ACQUIRE_FAILED,
          CALCULATION_FAILED,
          POINT_CREDIT_FAILED,
          UNKNOWN ->
          GlobalErrorCode.SERVER_ERROR;
    };
  }
}
