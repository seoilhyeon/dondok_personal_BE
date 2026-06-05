package com.oit.dondok.domain.point.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oit.dondok.domain.member.entity.Member;
import org.junit.jupiter.api.Test;

class PointHistoryTest {

  private static Member member() {
    return Member.create("member@example.com", "pw", "닉네임");
  }

  @Test
  void createCrewDepositReserveAllowsNegativeAmount() {
    PointHistory pointHistory =
        PointHistory.create(
            member(),
            -10_000L,
            0L,
            10_000L,
            0L,
            PointTransactionType.CREW_DEPOSIT_RESERVE,
            PointReferenceType.CREW_PARTICIPANT,
            1L,
            "crew:10:participant:1:reserve:1");

    assertThat(pointHistory.getAmount()).isEqualTo(-10_000L);
    assertThat(pointHistory.getAvailableAfter()).isZero();
    assertThat(pointHistory.getReservedAfter()).isEqualTo(10_000L);
    assertThat(pointHistory.getLockedAfter()).isZero();
    assertThat(pointHistory.getTransactionType())
        .isEqualTo(PointTransactionType.CREW_DEPOSIT_RESERVE);
    assertThat(pointHistory.getReferenceType()).isEqualTo(PointReferenceType.CREW_PARTICIPANT);
    assertThat(pointHistory.getReferenceId()).isEqualTo(1L);
    assertThat(pointHistory.getIdempotencyKey()).isEqualTo("crew:10:participant:1:reserve:1");
  }

  @Test
  void createPointChargeAllowsPositiveAmountAndZeroReferenceId() {
    PointHistory pointHistory =
        PointHistory.create(
            member(),
            10_000L,
            10_000L,
            0L,
            0L,
            PointTransactionType.POINT_CHARGE,
            PointReferenceType.POINT_CHARGE,
            0L,
            "charge:payment-id");

    assertThat(pointHistory.getAmount()).isEqualTo(10_000L);
    assertThat(pointHistory.getReferenceId()).isZero();
    assertThat(pointHistory.getReferenceType()).isEqualTo(PointReferenceType.POINT_CHARGE);
  }

  @Test
  void createCrewReserveReleaseAllowsCanonicalIdempotencyKey() {
    PointHistory pointHistory =
        PointHistory.create(
            member(),
            10_000L,
            10_000L,
            0L,
            0L,
            PointTransactionType.CREW_RESERVE_RELEASE,
            PointReferenceType.CREW_PARTICIPANT,
            1L,
            "crew:10:participant:1:reserve-release:1");

    assertThat(pointHistory.getTransactionType())
        .isEqualTo(PointTransactionType.CREW_RESERVE_RELEASE);
    assertThat(pointHistory.getReferenceId()).isEqualTo(1L);
    assertThat(pointHistory.getIdempotencyKey())
        .isEqualTo("crew:10:participant:1:reserve-release:1");
  }

  @Test
  void createCrewSettlementRefundAllowsCanonicalIdempotencyKey() {
    PointHistory pointHistory =
        PointHistory.create(
            member(),
            10_000L,
            10_000L,
            0L,
            0L,
            PointTransactionType.CREW_SETTLEMENT_REFUND,
            PointReferenceType.SETTLEMENT_ITEM,
            1L,
            "crew:10:participant:99:settlement-refund:final");

    assertThat(pointHistory.getTransactionType())
        .isEqualTo(PointTransactionType.CREW_SETTLEMENT_REFUND);
    assertThat(pointHistory.getReferenceType()).isEqualTo(PointReferenceType.SETTLEMENT_ITEM);
    assertThat(pointHistory.getReferenceId()).isEqualTo(1L);
    assertThat(pointHistory.getIdempotencyKey())
        .isEqualTo("crew:10:participant:99:settlement-refund:final");
  }

  @Test
  void createCrewSettlementRefundAllowsZeroAmount() {
    PointHistory pointHistory =
        PointHistory.create(
            member(),
            0L,
            0L,
            0L,
            0L,
            PointTransactionType.CREW_SETTLEMENT_REFUND,
            PointReferenceType.SETTLEMENT_ITEM,
            1L,
            "crew:10:participant:99:settlement-refund:final");

    assertThat(pointHistory.getAmount()).isZero();
    assertThat(pointHistory.getTransactionType())
        .isEqualTo(PointTransactionType.CREW_SETTLEMENT_REFUND);
  }

  @Test
  void createChargeRejectsPositiveAmountForPointChargeWhenReferenceIdIsNotZero() {
    assertThatThrownBy(
            () ->
                PointHistory.create(
                    member(),
                    10_000L,
                    10_000L,
                    0L,
                    0L,
                    PointTransactionType.POINT_CHARGE,
                    PointReferenceType.POINT_CHARGE,
                    1L,
                    "charge:payment-id"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsWrongReferenceMappingForTransactionType() {
    assertThatThrownBy(
            () ->
                PointHistory.create(
                    member(),
                    10_000L,
                    10_000L,
                    0L,
                    0L,
                    PointTransactionType.CREW_RESERVE_RELEASE,
                    PointReferenceType.SETTLEMENT_ITEM,
                    1L,
                    "crew:10:participant:1:reserve-release:1"))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () ->
                PointHistory.create(
                    member(),
                    10_000L,
                    10_000L,
                    0L,
                    0L,
                    PointTransactionType.CREW_SETTLEMENT_REFUND,
                    PointReferenceType.CREW_PARTICIPANT,
                    1L,
                    "crew:10:participant:1:settlement-refund:final"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsInvalidIdempotencyKeyFormat() {
    assertThatThrownBy(
            () ->
                PointHistory.create(
                    member(),
                    10_000L,
                    10_000L,
                    0L,
                    0L,
                    PointTransactionType.POINT_CHARGE,
                    PointReferenceType.POINT_CHARGE,
                    0L,
                    "payment-id"))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () ->
                PointHistory.create(
                    member(),
                    -10_000L,
                    0L,
                    10_000L,
                    0L,
                    PointTransactionType.CREW_DEPOSIT_RESERVE,
                    PointReferenceType.CREW_PARTICIPANT,
                    1L,
                    "crew:10:participant:1:reserve-release:1"))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () ->
                PointHistory.create(
                    member(),
                    10_000L,
                    10_000L,
                    0L,
                    0L,
                    PointTransactionType.CREW_SETTLEMENT_REFUND,
                    PointReferenceType.SETTLEMENT_ITEM,
                    1L,
                    "crew:10:participant:1:settlement-refund:1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsCrewParticipantIdMismatchBetweenReferenceAndIdempotencyKey() {
    assertThatThrownBy(
            () ->
                PointHistory.create(
                    member(),
                    -10_000L,
                    0L,
                    10_000L,
                    0L,
                    PointTransactionType.CREW_DEPOSIT_RESERVE,
                    PointReferenceType.CREW_PARTICIPANT,
                    1L,
                    "crew:10:participant:2:reserve:1"))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () ->
                PointHistory.create(
                    member(),
                    10_000L,
                    10_000L,
                    0L,
                    0L,
                    PointTransactionType.CREW_RESERVE_RELEASE,
                    PointReferenceType.CREW_PARTICIPANT,
                    1L,
                    "crew:10:participant:2:reserve-release:1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsBlankOrTooLongIdempotencyKey() {
    assertThatThrownBy(
            () ->
                PointHistory.create(
                    member(),
                    10_000L,
                    10_000L,
                    0L,
                    0L,
                    PointTransactionType.POINT_CHARGE,
                    PointReferenceType.POINT_CHARGE,
                    0L,
                    " "))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () ->
                PointHistory.create(
                    member(),
                    10_000L,
                    10_000L,
                    0L,
                    0L,
                    PointTransactionType.POINT_CHARGE,
                    PointReferenceType.POINT_CHARGE,
                    0L,
                    "charge:" + "a".repeat(154)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsNonNegativeAmountForCrewDepositReserve() {
    assertThatThrownBy(
            () ->
                PointHistory.create(
                    member(),
                    0L,
                    0L,
                    0L,
                    0L,
                    PointTransactionType.CREW_DEPOSIT_RESERVE,
                    PointReferenceType.CREW_PARTICIPANT,
                    1L,
                    "crew:10:participant:1:reserve:1"))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () ->
                PointHistory.create(
                    member(),
                    10_000L,
                    0L,
                    0L,
                    0L,
                    PointTransactionType.CREW_DEPOSIT_RESERVE,
                    PointReferenceType.CREW_PARTICIPANT,
                    1L,
                    "crew:10:participant:1:reserve:1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsNonPositiveAmountForPositiveTransactions() {
    assertThatThrownBy(
            () ->
                PointHistory.create(
                    member(),
                    -10_000L,
                    0L,
                    0L,
                    0L,
                    PointTransactionType.CREW_SETTLEMENT_REFUND,
                    PointReferenceType.SETTLEMENT_ITEM,
                    1L,
                    "crew:10:participant:1:settlement-refund:final"))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () ->
                PointHistory.create(
                    member(),
                    0L,
                    0L,
                    0L,
                    0L,
                    PointTransactionType.CREW_RESERVE_RELEASE,
                    PointReferenceType.CREW_PARTICIPANT,
                    1L,
                    "crew:10:participant:1:reserve-release:1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsNegativeBalanceSnapshots() {
    assertThatThrownBy(
            () ->
                PointHistory.create(
                    member(),
                    10_000L,
                    -1L,
                    0L,
                    0L,
                    PointTransactionType.POINT_CHARGE,
                    PointReferenceType.POINT_CHARGE,
                    0L,
                    "charge:payment-id"))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () ->
                PointHistory.create(
                    member(),
                    10_000L,
                    0L,
                    -1L,
                    0L,
                    PointTransactionType.CREW_RESERVE_RELEASE,
                    PointReferenceType.CREW_PARTICIPANT,
                    1L,
                    "crew:10:participant:1:reserve-release:1"))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () ->
                PointHistory.create(
                    member(),
                    10_000L,
                    0L,
                    0L,
                    -1L,
                    PointTransactionType.CREW_SETTLEMENT_REFUND,
                    PointReferenceType.SETTLEMENT_ITEM,
                    1L,
                    "crew:10:participant:1:settlement-refund:final"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsNegativeReferenceId() {
    assertThatThrownBy(
            () ->
                PointHistory.create(
                    member(),
                    10_000L,
                    10_000L,
                    0L,
                    0L,
                    PointTransactionType.POINT_CHARGE,
                    PointReferenceType.POINT_CHARGE,
                    -1L,
                    "charge:payment-id"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsNullRequiredValues() {
    assertThatThrownBy(
            () ->
                PointHistory.create(
                    null,
                    10_000L,
                    10_000L,
                    0L,
                    0L,
                    PointTransactionType.POINT_CHARGE,
                    PointReferenceType.POINT_CHARGE,
                    0L,
                    "charge:payment-id"))
        .isInstanceOf(NullPointerException.class);

    assertThatThrownBy(
            () ->
                PointHistory.create(
                    member(),
                    null,
                    10_000L,
                    0L,
                    0L,
                    PointTransactionType.POINT_CHARGE,
                    PointReferenceType.POINT_CHARGE,
                    0L,
                    "charge:payment-id"))
        .isInstanceOf(NullPointerException.class);

    assertThatThrownBy(
            () ->
                PointHistory.create(
                    member(),
                    10_000L,
                    10_000L,
                    0L,
                    0L,
                    null,
                    PointReferenceType.POINT_CHARGE,
                    0L,
                    "charge:payment-id"))
        .isInstanceOf(NullPointerException.class);

    assertThatThrownBy(
            () ->
                PointHistory.create(
                    member(),
                    10_000L,
                    10_000L,
                    0L,
                    0L,
                    PointTransactionType.POINT_CHARGE,
                    null,
                    0L,
                    "charge:payment-id"))
        .isInstanceOf(NullPointerException.class);

    assertThatThrownBy(
            () ->
                PointHistory.create(
                    member(),
                    10_000L,
                    10_000L,
                    0L,
                    0L,
                    PointTransactionType.POINT_CHARGE,
                    PointReferenceType.POINT_CHARGE,
                    null,
                    "charge:payment-id"))
        .isInstanceOf(NullPointerException.class);

    assertThatThrownBy(
            () ->
                PointHistory.create(
                    member(),
                    10_000L,
                    10_000L,
                    0L,
                    0L,
                    PointTransactionType.POINT_CHARGE,
                    PointReferenceType.POINT_CHARGE,
                    0L,
                    null))
        .isInstanceOf(NullPointerException.class);
  }
}
