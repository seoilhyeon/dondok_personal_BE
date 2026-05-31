package com.oit.dondok.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record LoginMemberResponse(
    @JsonProperty("member_uuid") UUID memberUuid, String email, String nickname) {}
