package com.oit.dondok.domain.point.repository;

import com.oit.dondok.domain.point.entity.PointReferenceType;
import com.oit.dondok.domain.point.entity.PointTransactionType;
import java.time.LocalDateTime;

public record PointHistoryItemProjection(
    Long pointHistoryId,
    Long amount,
    Long balanceAfter,
    PointTransactionType transactionType,
    PointReferenceType referenceType,
    Long referenceId,
    LocalDateTime createdAt) {}
