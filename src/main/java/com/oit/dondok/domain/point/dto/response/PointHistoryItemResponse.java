package com.oit.dondok.domain.point.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.point.entity.PointReferenceType;
import com.oit.dondok.domain.point.entity.PointTransactionType;
import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PointHistoryItemResponse(
    Long pointHistoryId,
    Long amount,
    Long balanceAfter,
    PointTransactionType transactionType,
    PointReferenceType referenceType,
    Long referenceId,
    PointReferenceMetaResponse referenceMeta,
    OffsetDateTime createdAt) {}
