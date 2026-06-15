package com.oit.dondok.domain.point.entity;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.global.entity.AuditableTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "point_account",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_point_account_member", columnNames = "member_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointAccount extends AuditableTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Column(name = "available_balance", nullable = false)
  private Long availableBalance;

  @Column(name = "reserved_balance", nullable = false)
  private Long reservedBalance;

  @Column(name = "locked_balance", nullable = false)
  private Long lockedBalance;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  private PointAccount(
      Member member, Long availableBalance, Long reservedBalance, Long lockedBalance) {
    Objects.requireNonNull(member, "member는 필수값 입니다.");
    Objects.requireNonNull(availableBalance, "availableBalance는 필수값 입니다.");
    Objects.requireNonNull(reservedBalance, "reservedBalance는 필수값 입니다.");
    Objects.requireNonNull(lockedBalance, "lockedBalance는 필수값 입니다.");

    if (availableBalance < 0) {
      throw new IllegalArgumentException("availableBalance는 0이상이어야합니다.");
    }
    if (reservedBalance < 0) {
      throw new IllegalArgumentException("reservedBalance는 0이상이어야합니다.");
    }
    if (lockedBalance < 0) {
      throw new IllegalArgumentException("lockedBalance는 0이상이어야합니다.");
    }

    this.member = member;
    this.availableBalance = availableBalance;
    this.reservedBalance = reservedBalance;
    this.lockedBalance = lockedBalance;
  }

  public static PointAccount create(Member member) {
    return new PointAccount(member, 0L, 0L, 0L);
  }

  public void increaseAvailable(long amount) {
    validatePositiveAmount(amount, "amount는 0보다 커야합니다.");
    this.availableBalance += amount;
  }

  public void reserve(long amount) {
    validatePositiveAmount(amount, "amount는 0보다 커야합니다.");
    validateSufficientBalance(availableBalance, amount, "availableBalance가 부족합니다.");

    this.availableBalance -= amount;
    this.reservedBalance += amount;
  }

  public void lockFromAvailable(long amount) {
    validatePositiveAmount(amount, "amount는 0보다 커야합니다.");
    validateSufficientBalance(availableBalance, amount, "availableBalance가 부족합니다.");

    this.availableBalance -= amount;
    this.lockedBalance += amount;
  }

  public void lockFromReserved(long amount) {
    validatePositiveAmount(amount, "amount는 0보다 커야합니다.");
    validateSufficientBalance(reservedBalance, amount, "reservedBalance가 부족합니다.");

    this.reservedBalance -= amount;
    this.lockedBalance += amount;
  }

  public void releaseReserved(long amount) {
    validatePositiveAmount(amount, "amount는 0보다 커야합니다.");
    validateSufficientBalance(reservedBalance, amount, "reservedBalance가 부족합니다.");

    this.reservedBalance -= amount;
    this.availableBalance += amount;
  }

  public void releaseLockedToAvailable(long amount) {
    validatePositiveAmount(amount, "amount는 0보다 커야합니다.");
    validateSufficientBalance(lockedBalance, amount, "lockedBalance가 부족합니다.");

    this.lockedBalance -= amount;
    this.availableBalance += amount;
  }

  public void settleLockedDeposit(long depositAmount, long refundAmount) {
    validatePositiveAmount(depositAmount, "depositAmount는 0보다 커야합니다.");
    validateNonNegativeAmount(refundAmount, "refundAmount는 0 이상이어야합니다.");
    validateSufficientBalance(lockedBalance, depositAmount, "lockedBalance가 부족합니다.");

    this.lockedBalance -= depositAmount;
    this.availableBalance += refundAmount;
  }

  private static void validatePositiveAmount(long amount, String message) {
    if (amount <= 0) {
      throw new IllegalArgumentException(message);
    }
  }

  private static void validateNonNegativeAmount(long amount, String message) {
    if (amount < 0) {
      throw new IllegalArgumentException(message);
    }
  }

  private static void validateSufficientBalance(
      long balance, long amount, String insufficientMessage) {
    if (balance < amount) {
      throw new IllegalArgumentException(insufficientMessage);
    }
  }
}
