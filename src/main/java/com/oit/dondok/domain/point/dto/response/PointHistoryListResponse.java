package com.oit.dondok.domain.point.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PointHistoryListResponse(List<PointHistoryItemResponse> items, String nextCursor) {}
