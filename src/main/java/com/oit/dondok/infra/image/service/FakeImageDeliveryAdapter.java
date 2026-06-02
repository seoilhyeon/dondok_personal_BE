package com.oit.dondok.infra.image.service;

import com.oit.dondok.domain.image.port.ImageDeliveryPort;
import com.oit.dondok.domain.image.port.ImageDeliveryUrl;
import com.oit.dondok.domain.image.port.ImageObjectKey;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "dev", "test", "integration"})
public class FakeImageDeliveryAdapter implements ImageDeliveryPort {

  private static final String CDN_BASE_URL = "https://cdn.example.com";
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  @Override
  public ImageDeliveryUrl createDeliveryUrl(ImageObjectKey key, Duration ttl) {
    return new ImageDeliveryUrl(
        CDN_BASE_URL + "/" + key.value(), OffsetDateTime.now(SEOUL).plus(ttl));
  }
}
