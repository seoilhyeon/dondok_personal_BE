package com.oit.dondok.infra.image.adapter;

import com.oit.dondok.domain.image.port.ImageDeliveryPort;
import com.oit.dondok.domain.image.port.ImageDeliveryUrl;
import com.oit.dondok.domain.image.port.ImageObjectKey;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// ImageDeliveryPort의 test 전용 stub. 실제 presigned GET 없이 컨텍스트를 부팅하기 위한 구현으로,
// test 외 모든 프로파일에서 등록되는 S3ImageDeliveryAdapter와 상호배타적이다.
@Component
@Profile({"test"})
public class StubImageDeliveryAdapter implements ImageDeliveryPort {

  private static final String CDN_BASE_URL = "https://cdn.example.com";
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  @Override
  public ImageDeliveryUrl createDeliveryUrl(ImageObjectKey key, Duration ttl) {
    return new ImageDeliveryUrl(
        CDN_BASE_URL + "/" + key.value(), OffsetDateTime.now(SEOUL).plus(ttl));
  }
}
