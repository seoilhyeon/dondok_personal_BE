package com.oit.dondok.domain.crew.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record AddReactionRequest(@NotBlank @JsonProperty("reaction_type") String reactionType) {}
