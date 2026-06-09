package com.oit.dondok.domain.crew.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ParticipationCountResponse(
        long pendingCount,
        long lockedCount,
        long rejectedCount) {

    public static ParticipationCountResponse of(long pending, long locked, long rejected) {
        return new ParticipationCountResponse(pending, locked, rejected);
    }
}