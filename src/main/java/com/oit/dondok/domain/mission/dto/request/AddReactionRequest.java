package com.oit.dondok.domain.mission.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

// reaction_type 검증(trim/blank/char_length<=20 → INVALID_REACTION_TYPE)은 서비스에서 수행한다.
// bean validation(@NotBlank/@Size)을 쓰면 blank가 INVALID_INPUT으로 빠져 계약 에러코드와 어긋나므로 두지 않는다.
public record AddReactionRequest(@JsonProperty("reaction_type") String reactionType) {}
