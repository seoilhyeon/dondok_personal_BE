package com.oit.dondok.domain.point.repository;

import com.oit.dondok.domain.point.entity.PointReferenceType;
import com.oit.dondok.domain.point.entity.WalletHistoryDisplayType;
import com.oit.dondok.domain.point.entity.WalletHistoryStatus;
import java.time.LocalDateTime;

public record WalletHistoryEventProjection(
    String walletEventId,
    Long amount,
    Long balanceAfter,
    WalletHistoryDisplayType displayType,
    WalletHistoryStatus status,
    PointReferenceType referenceType,
    Long referenceId,
    LocalDateTime createdAt) {}
