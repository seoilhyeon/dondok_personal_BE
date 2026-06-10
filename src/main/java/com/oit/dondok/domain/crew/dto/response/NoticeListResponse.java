package com.oit.dondok.domain.crew.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record NoticeListResponse(
    List<NoticeItemResponse> items, @JsonProperty("next_cursor") String nextCursor) {}
