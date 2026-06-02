package com.oit.dondok.domain.image.port;

import java.time.OffsetDateTime;

public record PresignedUpload(String uploadUrl, ImageObjectKey key, OffsetDateTime expiresAt) {}
