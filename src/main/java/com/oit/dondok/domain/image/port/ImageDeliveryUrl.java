package com.oit.dondok.domain.image.port;

import java.time.OffsetDateTime;

public record ImageDeliveryUrl(String url, OffsetDateTime expiresAt) {}
