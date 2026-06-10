package com.oit.dondok.domain.crew.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record ReactionResponse(
    @JsonProperty("notice_id") Long noticeId,
    @JsonProperty("my_reactions") List<String> myReactions,
    @JsonProperty("reaction_counts") Map<String, Long> reactionCounts) {}
