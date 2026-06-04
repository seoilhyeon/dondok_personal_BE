package com.oit.dondok.infra.image.adapter;

import com.oit.dondok.domain.image.port.ImageDeliveryPort;
import com.oit.dondok.domain.image.port.ImageDeliveryUrl;
import com.oit.dondok.domain.image.port.ImageObjectKey;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

// ImageDeliveryPort의 실제 구현. StubImageDeliveryAdapter(test 전용)와 상호배타적으로
// test를 제외한 모든 프로파일(local/dev/prod/integration)에서 등록된다.
// 현재는 S3 presigned GET으로 발급하지만, 전송 기술(CloudFront/CDN 등) 선택은 이 어댑터 내부에만 머문다.
@Component
@Profile("!test")
@RequiredArgsConstructor
public class S3ImageDeliveryAdapter implements ImageDeliveryPort {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final S3Presigner s3Presigner;

  @Value("${app.aws.s3.bucket}")
  private String bucket;

  // 표시용 GET을 허용하는 서명된 URL을 발급한다. 만료(ttl)는 호출자(도메인 서비스)가 결정한다.
  @Override
  public ImageDeliveryUrl createDeliveryUrl(ImageObjectKey key, Duration ttl) {
    GetObjectPresignRequest presignRequest =
        GetObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .getObjectRequest(r -> r.bucket(bucket).key(key.value()))
            .build();

    String url = s3Presigner.presignGetObject(presignRequest).url().toString();
    OffsetDateTime expiresAt = OffsetDateTime.now(SEOUL).plus(ttl);
    return new ImageDeliveryUrl(url, expiresAt);
  }
}
