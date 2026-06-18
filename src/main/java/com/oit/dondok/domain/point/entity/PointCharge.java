package com.oit.dondok.domain.point.entity;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.global.entity.AuditableTimeEntity;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "point_charge",
    indexes = {
      @Index(name = "idx_point_charge_member_created", columnList = "member_id, created_at"),
      @Index(name = "idx_point_charge_status_created", columnList = "status, created_at")
    },
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_point_charge_payment_id", columnNames = "payment_id"),
      @UniqueConstraint(name = "uk_point_charge_order_id", columnNames = "order_id"),
      @UniqueConstraint(name = "uk_point_charge_point_history", columnNames = "point_history_id")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointCharge extends AuditableTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Column(name = "payment_id", nullable = false, length = 200)
  private String paymentId;

  @Column(name = "order_id", nullable = false, length = 64)
  private String orderId;

  @Column(name = "amount", nullable = false)
  private Long amount;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private PointChargeStatus status;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "point_history_id")
  private PointHistory pointHistory;

  @Column(name = "failure_code", length = 80)
  private String failureCode;

  @Column(name = "failure_message", length = 500)
  private String failureMessage;

  @Column(name = "recovery_attempt_count", nullable = false)
  private int recoveryAttemptCount;

  @Column(name = "next_recovery_at")
  private LocalDateTime nextRecoveryAt;

  private PointCharge(Member member, String paymentId, String orderId, Long amount) {
    this.member = Objects.requireNonNull(member, "member must not be null");
    this.paymentId = requireText(paymentId, "paymentId");
    this.orderId = requireText(orderId, "orderId");
    this.amount = requirePositive(amount);
    this.status = PointChargeStatus.PENDING_CONFIRM;
  }

  public static PointCharge createPending(
      Member member, String paymentId, String orderId, Long amount) {
    return new PointCharge(member, paymentId, orderId, amount);
  }

  public boolean isLinked() {
    return pointHistory != null;
  }

  public boolean matches(Member member, String orderId, Long amount) {
    return Objects.equals(this.member.getId(), member.getId())
        && Objects.equals(this.orderId, orderId)
        && Objects.equals(this.amount, amount);
  }

  public boolean belongsTo(Member member) {
    return Objects.equals(this.member.getId(), member.getId());
  }

  public void complete(PointHistory pointHistory) {
    this.pointHistory = Objects.requireNonNull(pointHistory, "pointHistory must not be null");
    this.status = PointChargeStatus.COMPLETED;
    this.failureCode = null;
    this.failureMessage = null;
    this.nextRecoveryAt = null;
  }

  public void fail(String failureCode, String failureMessage) {
    if (isLinked()) {
      return;
    }
    this.status = PointChargeStatus.CONFIRM_FAILED;
    this.failureCode = sanitize(failureCode, 80);
    this.failureMessage = sanitize(failureMessage, 500);
    this.nextRecoveryAt = null;
  }

  public void recordRecoveryAttempt(LocalDateTime nextRecoveryAt) {
    this.recoveryAttemptCount += 1;
    this.nextRecoveryAt = Objects.requireNonNull(nextRecoveryAt, "nextRecoveryAt must not be null");
  }

  public void reserveRecovery(LocalDateTime nextRecoveryAt) {
    this.nextRecoveryAt = Objects.requireNonNull(nextRecoveryAt, "nextRecoveryAt must not be null");
  }

  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value.trim();
  }

  private static Long requirePositive(Long value) {
    if (value == null || value <= 0) {
      throw new IllegalArgumentException("amount must be positive");
    }
    return value;
  }

  private static String sanitize(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    String sanitized = value.replaceAll("[\\r\\n]", " ").trim();
    return sanitized.substring(0, Math.min(sanitized.length(), maxLength));
  }
}
