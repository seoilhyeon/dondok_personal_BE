package com.oit.dondok.domain.point.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oit.dondok.domain.member.entity.Member;
import org.junit.jupiter.api.Test;

class PointAccountTest {

  private static Member member() {
    return Member.create("member@example.com", "pw", "닉네임");
  }

  @Test
  void createDefaultsAvailableReservedLockedToZero() {
    PointAccount account = PointAccount.create(member());

    assertThat(account.getAvailableBalance()).isEqualTo(0L);
    assertThat(account.getReservedBalance()).isEqualTo(0L);
    assertThat(account.getLockedBalance()).isEqualTo(0L);
  }

  @Test
  void createFailsWhenMemberIsNull() {
    assertThatThrownBy(() -> PointAccount.create(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void increaseAvailableAddsUsableBalance() {
    PointAccount account = PointAccount.create(member());

    account.increaseAvailable(10_000L);

    assertThat(account.getAvailableBalance()).isEqualTo(10_000L);
    assertThat(account.getReservedBalance()).isZero();
    assertThat(account.getLockedBalance()).isZero();
  }

  @Test
  void increaseAvailableFailsWhenAmountIsNotPositive() {
    PointAccount account = PointAccount.create(member());

    assertThatThrownBy(() -> account.increaseAvailable(0L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> account.increaseAvailable(-1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reserveMovesFromAvailableToReserved() {
    PointAccount account = PointAccount.create(member());

    account.increaseAvailable(10_000L);
    account.reserve(3_000L);

    assertThat(account.getAvailableBalance()).isEqualTo(7_000L);
    assertThat(account.getReservedBalance()).isEqualTo(3_000L);
  }

  @Test
  void reserveFailsWhenAmountIsNotPositive() {
    PointAccount account = PointAccount.create(member());

    assertThatThrownBy(() -> account.reserve(0L)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> account.reserve(-1L)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reserveFailsWhenAvailableIsInsufficient() {
    PointAccount account = PointAccount.create(member());

    assertThatThrownBy(() -> account.reserve(1L)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void lockFromAvailableMovesAvailableToLocked() {
    PointAccount account = PointAccount.create(member());

    account.increaseAvailable(10_000L);
    account.lockFromAvailable(4_000L);

    assertThat(account.getAvailableBalance()).isEqualTo(6_000L);
    assertThat(account.getReservedBalance()).isZero();
    assertThat(account.getLockedBalance()).isEqualTo(4_000L);
  }

  @Test
  void lockFromAvailableFailsWhenAmountIsNotPositiveOrAvailableIsInsufficient() {
    PointAccount account = PointAccount.create(member());

    assertThatThrownBy(() -> account.lockFromAvailable(0L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> account.lockFromAvailable(-1L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> account.lockFromAvailable(1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void lockFromReservedMovesReservedToLocked() {
    PointAccount account = PointAccount.create(member());

    account.increaseAvailable(10_000L);
    account.reserve(7_000L);
    account.lockFromReserved(4_000L);

    assertThat(account.getAvailableBalance()).isEqualTo(3_000L);
    assertThat(account.getReservedBalance()).isEqualTo(3_000L);
    assertThat(account.getLockedBalance()).isEqualTo(4_000L);
  }

  @Test
  void lockFromReservedFailsWhenAmountIsNotPositiveOrReservedIsInsufficient() {
    PointAccount account = PointAccount.create(member());

    assertThatThrownBy(() -> account.lockFromReserved(0L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> account.lockFromReserved(-1L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> account.lockFromReserved(1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void releaseReservedMovesReservedBackToAvailable() {
    PointAccount account = PointAccount.create(member());

    account.increaseAvailable(10_000L);
    account.reserve(7_000L);
    account.releaseReserved(4_000L);

    assertThat(account.getAvailableBalance()).isEqualTo(7_000L);
    assertThat(account.getReservedBalance()).isEqualTo(3_000L);
    assertThat(account.getLockedBalance()).isZero();
  }

  @Test
  void releaseReservedFailsWhenAmountIsNotPositiveOrReservedIsInsufficient() {
    PointAccount account = PointAccount.create(member());

    assertThatThrownBy(() -> account.releaseReserved(0L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> account.releaseReserved(-1L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> account.releaseReserved(1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void settleLockedDepositRemovesDepositAndRefundsFinalAmount() {
    PointAccount account = PointAccount.create(member());

    account.increaseAvailable(100_000L);
    account.lockFromAvailable(100_000L);
    account.settleLockedDeposit(100_000L, 70_000L);

    assertThat(account.getAvailableBalance()).isEqualTo(70_000L);
    assertThat(account.getLockedBalance()).isEqualTo(0L);
  }

  @Test
  void settleLockedDepositAllowsZeroRefund() {
    PointAccount account = PointAccount.create(member());

    account.increaseAvailable(100_000L);
    account.lockFromAvailable(100_000L);
    account.settleLockedDeposit(100_000L, 0L);

    assertThat(account.getAvailableBalance()).isZero();
    assertThat(account.getLockedBalance()).isZero();
  }

  @Test
  void settleLockedDepositAllowsRefundExceedingOwnDeposit() {
    PointAccount account = PointAccount.create(member());

    account.increaseAvailable(100_000L);
    account.lockFromAvailable(100_000L);
    account.settleLockedDeposit(100_000L, 150_000L);

    assertThat(account.getAvailableBalance()).isEqualTo(150_000L);
    assertThat(account.getLockedBalance()).isZero();
  }

  @Test
  void settleLockedDepositFailsWhenLockedBalanceIsInsufficient() {
    PointAccount account = PointAccount.create(member());

    assertThatThrownBy(() -> account.settleLockedDeposit(1L, 0L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void settleLockedDepositFailsWhenDepositIsNotPositiveOrRefundIsNegative() {
    PointAccount account = PointAccount.create(member());

    assertThatThrownBy(() -> account.settleLockedDeposit(0L, 0L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> account.settleLockedDeposit(-1L, 0L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> account.settleLockedDeposit(1L, -1L))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
