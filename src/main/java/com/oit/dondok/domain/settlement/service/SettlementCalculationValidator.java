package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.settlement.entity.RemainderPolicy;
import com.oit.dondok.domain.settlement.service.model.SettlementCalculationInput;
import com.oit.dondok.domain.settlement.service.model.SettlementParticipantInput;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SettlementCalculationValidator {
  private SettlementCalculationValidator() {}

  public static void validate(SettlementCalculationInput input) {
    if (input == null) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }

    validateRemainderPolicy(input.remainderPolicy());
    validateParticipants(input.participants(), input.remainderPolicy());
  }

  private static void validateRemainderPolicy(RemainderPolicy remainderPolicy) {
    if (remainderPolicy == null || remainderPolicy != RemainderPolicy.HOST_REMAINDER) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
  }

  private static void validateParticipants(
      List<SettlementParticipantInput> participants, RemainderPolicy policy) {
    if (participants == null || participants.isEmpty()) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }

    Set<String> participantKeys = new HashSet<>();
    int hostCount = 0;

    for (SettlementParticipantInput participant : participants) {
      if (participant == null) {
        throw new CustomException(GlobalErrorCode.INVALID_INPUT);
      }
      if (participant.host()) {
        hostCount++;
      }
      if (!participantKeys.add(participant.participantKey())) {
        throw new CustomException(GlobalErrorCode.INVALID_INPUT);
      }
    }

    if (policy == RemainderPolicy.HOST_REMAINDER && hostCount != 1) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
  }
}
