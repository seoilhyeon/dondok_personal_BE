package com.oit.dondok.domain.point.repository;

import java.time.LocalDateTime;

public record PointBalanceProjection(
    Long availableBalance,
    Long reservedBalance,
    Long activeLockedAmount,
    Long settlementPendingAmount,
    Long lockedBalance,
    LocalDateTime updatedAt) {}
