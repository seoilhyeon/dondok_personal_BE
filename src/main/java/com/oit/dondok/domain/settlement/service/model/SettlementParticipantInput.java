package com.oit.dondok.domain.settlement.service.model;

import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;

public record SettlementParticipantInput(
    String participantKey,
    boolean host,
    long depositAmount,
    int successCountRaw,
    int recognizedSuccessCount,
    int recognizedDatesCount,
    int excludedSuccessCount) {

  public SettlementParticipantInput {
    validate(
        participantKey,
        depositAmount,
        successCountRaw,
        recognizedSuccessCount,
        recognizedDatesCount,
        excludedSuccessCount);
  }

  private static void validate(
      String participantKey,
      long depositAmount,
      int successCountRaw,
      int recognizedSuccessCount,
      int recognizedDatesCount,
      int excludedSuccessCount) {
    if (participantKey == null || participantKey.isBlank()) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
    if (depositAmount <= 0
        || successCountRaw < 0
        || recognizedSuccessCount < 0
        || recognizedDatesCount < 0
        || excludedSuccessCount < 0) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
    if (successCountRaw < recognizedSuccessCount) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
    if (excludedSuccessCount != successCountRaw - recognizedSuccessCount) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
  }
}
