package com.oit.dondok.domain.point.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.point.entity.PointTransactionType;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PointChargeResponse(
    Long pointHistoryId,
    UUID memberUuid,
    Long amount,
    Long balanceAfter,
    PointTransactionType transactionType,
    OffsetDateTime createdAt) {}
