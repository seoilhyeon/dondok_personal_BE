package com.oit.dondok.domain.settlement.service.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import org.junit.jupiter.api.Test;

class SettlementParticipantInputTest {

  @Test
  void acceptsValidInput() {
    new SettlementParticipantInput("p1", true, 100L, 5, 3, 4, 2);
  }

  @Test
  void rejectsBlankParticipantKey() {
    assertThatThrownBy(() -> new SettlementParticipantInput(" ", true, 100L, 5, 3, 4, 2))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT));
  }

  @Test
  void rejectsNegativeCountsOrDeposit() {
    assertThatThrownBy(() -> new SettlementParticipantInput("p1", true, -1L, 5, 3, 4, 2))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT));

    assertThatThrownBy(() -> new SettlementParticipantInput("p1", true, 100L, -1, 3, 4, 2))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT));

    assertThatThrownBy(() -> new SettlementParticipantInput("p1", true, 100L, 3, 2, -1, 1))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT));
  }

  @Test
  void rejectsSuccessCountLessThanRecognizedCount() {
    assertThatThrownBy(() -> new SettlementParticipantInput("p1", true, 100L, 1, 2, 3, 0))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT));
  }

  @Test
  void rejectsExcludedCountMismatch() {
    assertThatThrownBy(() -> new SettlementParticipantInput("p1", true, 100L, 5, 3, 4, 1))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT));
  }
}
