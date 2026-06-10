package com.oit.dondok.domain.crew.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddReactionRequest(
    @NotBlank @Size(max = 20) @JsonProperty("reaction_type") String reactionType) {}
