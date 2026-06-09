package com.oit.dondok.domain.point.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PointBalanceResponse(
    Long availableBalance,
    Long reservedBalance,
    Long activeLockedAmount,
    Long settlementPendingAmount,
    Long lockedBalance,
    Long totalBalance,
    OffsetDateTime updatedAt) {}
