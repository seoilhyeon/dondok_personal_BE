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

  @Column(name = "idempotency_key", nullable = false, unique = true, length = 160)
  private String idempotencyKey;
}
