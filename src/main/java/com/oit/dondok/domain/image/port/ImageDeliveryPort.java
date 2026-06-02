package com.oit.dondok.domain.image.port;

import java.time.Duration;

public interface ImageDeliveryPort {

  ImageDeliveryUrl createDeliveryUrl(ImageObjectKey key, Duration ttl);
}
