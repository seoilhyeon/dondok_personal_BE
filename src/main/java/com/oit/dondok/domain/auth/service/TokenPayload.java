package com.oit.dondok.domain.auth.service;

import java.time.LocalDateTime;
import java.util.UUID;

public record TokenPayload(UUID memberUuid, LocalDateTime issuedAt, LocalDateTime expiresAt) {}
