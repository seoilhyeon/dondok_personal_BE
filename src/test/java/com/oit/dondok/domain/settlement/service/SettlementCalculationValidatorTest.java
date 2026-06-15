package com.oit.dondok.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oit.dondok.domain.settlement.entity.RemainderPolicy;
import com.oit.dondok.domain.settlement.service.model.SettlementCalculationInput;
import com.oit.dondok.domain.settlement.service.model.SettlementParticipantInput;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettlementCalculationValidatorTest {

  @Test
  void acceptsValidInput() {
    SettlementCalculationInput input =
        new SettlementCalculationInput(
            RemainderPolicy.HOST_REMAINDER,
            List.of(
                participant(1L, true, 100L, 5, 3, 4, 2), participant(2L, false, 50L, 5, 2, 2, 3)));
    SettlementCalculationValidator.validate(input);
  }

  @Test
  void rejectsNullInput() {
    assertThatThrownBy(() -> SettlementCalculationValidator.validate(null))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT));
  }

  @Test
  void rejectsNullRemainderPolicy() {
    SettlementCalculationInput input =
        new SettlementCalculationInput(
            null, List.of(new SettlementParticipantInput(1L, true, 100L, 5, 3, 4, 2)));

    assertThatThrownBy(() -> SettlementCalculationValidator.validate(input))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT));
  }

  @Test
  void rejectsNullOrEmptyParticipants() {
    assertThatThrownBy(
            () ->
                SettlementCalculationValidator.validate(
                    new SettlementCalculationInput(RemainderPolicy.HOST_REMAINDER, null)))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT));

    assertThatThrownBy(
            () ->
                SettlementCalculationValidator.validate(
                    new SettlementCalculationInput(RemainderPolicy.HOST_REMAINDER, List.of())))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT));
  }

  @Test
  void rejectsNullParticipant() {
    assertThatThrownBy(
            () ->
                SettlementCalculationValidator.validate(
                    new SettlementCalculationInput(
                        RemainderPolicy.HOST_REMAINDER,
                        Arrays.asList((SettlementParticipantInput) null))))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT));
  }

  @Test
  void rejectsDuplicateParticipantKey() {
    SettlementCalculationInput input =
        new SettlementCalculationInput(
            RemainderPolicy.HOST_REMAINDER,
            List.of(
                new SettlementParticipantInput(1L, true, 100L, 5, 3, 4, 2),
                new SettlementParticipantInput(1L, false, 50L, 5, 2, 2, 3)));

    assertThatThrownBy(() -> SettlementCalculationValidator.validate(input))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT));
  }

  @Test
  void rejectsHostCountNotOneForHostRemainder() {
    SettlementCalculationInput noHost =
        new SettlementCalculationInput(
            RemainderPolicy.HOST_REMAINDER,
            List.of(
                new SettlementParticipantInput(1L, false, 100L, 5, 3, 4, 2),
                new SettlementParticipantInput(2L, false, 50L, 5, 2, 2, 3)));

    SettlementCalculationInput twoHost =
        new SettlementCalculationInput(
            RemainderPolicy.HOST_REMAINDER,
            List.of(
                new SettlementParticipantInput(1L, true, 100L, 5, 3, 4, 2),
                new SettlementParticipantInput(2L, true, 50L, 5, 2, 2, 3)));

    assertThatThrownBy(() -> SettlementCalculationValidator.validate(noHost))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT));
    assertThatThrownBy(() -> SettlementCalculationValidator.validate(twoHost))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT));
  }

  private SettlementParticipantInput participant(
      long participantKey,
      boolean host,
      long depositAmount,
      int successCountRaw,
      int recognizedSuccessCount,
      int recognizedDatesCount,
      int excludedSuccessCount) {
    return new SettlementParticipantInput(
        participantKey,
        host,
        depositAmount,
        successCountRaw,
        recognizedSuccessCount,
        recognizedDatesCount,
        excludedSuccessCount);
  }
}
