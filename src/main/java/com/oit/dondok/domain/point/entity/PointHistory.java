package com.oit.dondok.domain.point.entity;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.global.entity.CreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "point_history",
    indexes = {
      @Index(name = "idx_point_history_member_created", columnList = "member_id, created_at"),
      @Index(name = "idx_point_history_reference", columnList = "reference_type, reference_id")
    },
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_point_history_idempotency_key",
            columnNames = "idempotency_key"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointHistory extends CreatedTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Column(name = "amount", nullable = false)
  private Long amount;

  @Column(name = "available_after", nullable = false)
  private Long availableAfter;

  @Column(name = "reserved_after", nullable = false)
  private Long reservedAfter;

  @Column(name = "locked_after", nullable = false)
  private Long lockedAfter;

  @Enumerated(EnumType.STRING)
  @Column(name = "transaction_type", nullable = false, length = 40)
  private PointTransactionType transactionType;

  @Enumerated(EnumType.STRING)
  @Column(name = "reference_type", nullable = false, length = 40)
  private PointReferenceType referenceType;

  @Column(name = "reference_id", nullable = false)
  private Long referenceId;

  @Column(name = "idempotency_key", nullable = false, length = 160)
  private String idempotencyKey;

  private PointHistory(
      Member member,
      Long amount,
      Long availableAfter,
      Long reservedAfter,
      Long lockedAfter,
      PointTransactionType transactionType,
      PointReferenceType referenceType,
      Long referenceId,
      String idempotencyKey) {
    Objects.requireNonNull(member, "member는 필수값 입니다.");
    Objects.requireNonNull(amount, "amount는 필수값 입니다.");
    Objects.requireNonNull(availableAfter, "availableAfter는 필수값 입니다.");
    Objects.requireNonNull(reservedAfter, "reservedAfter는 필수값 입니다.");
    Objects.requireNonNull(lockedAfter, "lockedAfter는 필수값 입니다.");
    Objects.requireNonNull(transactionType, "transactionType는 필수값 입니다.");
    Objects.requireNonNull(referenceType, "referenceType는 필수값 입니다.");
    Objects.requireNonNull(referenceId, "referenceId는 필수값 입니다.");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey는 필수값 입니다.");

    validateAmountSign(amount, transactionType);
    if (availableAfter < 0) {
      throw new IllegalArgumentException("availableAfter는 0 이상이어야 합니다.");
    }
    if (reservedAfter < 0) {
      throw new IllegalArgumentException("reservedAfter는 0 이상이어야 합니다.");
    }
    if (lockedAfter < 0) {
      throw new IllegalArgumentException("lockedAfter는 0 이상이어야 합니다.");
    }
    if (referenceId < 0) {
      throw new IllegalArgumentException("referenceId는 0 이상이어야 합니다.");
    }
    PointHistoryIdempotencyKeyValidator.validate(
        transactionType, referenceType, referenceId, idempotencyKey);

    this.member = member;
    this.amount = amount;
    this.availableAfter = availableAfter;
    this.reservedAfter = reservedAfter;
    this.lockedAfter = lockedAfter;
    this.transactionType = transactionType;
    this.referenceType = referenceType;
    this.referenceId = referenceId;
    this.idempotencyKey = idempotencyKey;
  }

  private static void validateAmountSign(Long amount, PointTransactionType transactionType) {
    if (transactionType == PointTransactionType.CREW_DEPOSIT_RESERVE) {
      if (amount >= 0) {
        throw new IllegalArgumentException("보증금 예치는 음수 금액이어야 합니다.");
      }
      return;
    }
    if (amount <= 0) {
      throw new IllegalArgumentException("충전/반환/환급은 양수 금액이어야 합니다.");
    }
  }

  public static PointHistory create(
      Member member,
      Long amount,
      Long availableAfter,
      Long reservedAfter,
      Long lockedAfter,
      PointTransactionType transactionType,
      PointReferenceType referenceType,
      Long referenceId,
      String idempotencyKey) {
    return new PointHistory(
        member,
        amount,
        availableAfter,
        reservedAfter,
        lockedAfter,
        transactionType,
        referenceType,
        referenceId,
        idempotencyKey);
  }
}
