package com.oit.dondok.domain.point.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.point.entity.PointReferenceType;
import com.oit.dondok.domain.point.entity.WalletHistoryDisplayType;
import com.oit.dondok.domain.point.entity.WalletHistoryStatus;
import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record WalletHistoryItemResponse(
    String walletEventId,
    Long amount,
    Long balanceAfter,
    WalletHistoryDisplayType displayType,
    WalletHistoryStatus status,
    PointReferenceType referenceType,
    Long referenceId,
    PointReferenceMetaResponse referenceMeta,
    OffsetDateTime createdAt) {}
