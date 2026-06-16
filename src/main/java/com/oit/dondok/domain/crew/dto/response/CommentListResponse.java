package com.oit.dondok.domain.crew.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CommentListResponse(
    List<CommentItemResponse> items, @JsonProperty("next_cursor") String nextCursor) {}
