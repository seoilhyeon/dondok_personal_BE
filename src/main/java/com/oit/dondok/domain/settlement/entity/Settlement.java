package com.oit.dondok.domain.settlement.entity;

import com.oit.dondok.domain.crew.entity.Crew;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "settlement",
    indexes =
        @Index(
            name = "idx_settlement_status_retry_created",
            columnList = "status, retry_count, created_at"),
    uniqueConstraints = @UniqueConstraint(name = "uk_settlement_crew", columnNames = "crew_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement extends AuditableTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "crew_id", nullable = false)
  private Crew crew;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private SettlementStatus status;

  @Column(name = "baseline_frozen_at", nullable = false)
  private LocalDateTime baselineFrozenAt;

  @Column(name = "batch_run_key", length = 100)
  private String batchRunKey;

  @Column(name = "retry_count", nullable = false)
  private Integer retryCount;

  @Column(name = "total_participants", nullable = false)
  private Integer totalParticipants;

  @Column(name = "total_locked_amount", nullable = false)
  private Long totalLockedAmount;

  @Column(name = "total_recognized_success", nullable = false)
  private Integer totalRecognizedSuccess;

  @Column(name = "total_base_refund_amount", nullable = false)
  private Long totalBaseRefundAmount;

  @Column(name = "total_remainder_amount", nullable = false)
  private Long totalRemainderAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "remainder_policy", nullable = false, length = 40)
  private RemainderPolicy remainderPolicy;

  @Enumerated(EnumType.STRING)
  @Column(name = "failure_code", length = 50)
  private SettlementFailureCode failureCode;

  @Column(name = "failure_message", length = 500)
  private String failureMessage;

  @Column(name = "algorithm_version", nullable = false, length = 50)
  private String algorithmVersion;

  @Column(name = "rule_context_snapshot", nullable = false, columnDefinition = "json")
  private String ruleContextSnapshot;

  @Column(name = "started_at")
  private LocalDateTime startedAt;

  @Column(name = "finished_at")
  private LocalDateTime finishedAt;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;
}
