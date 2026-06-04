package com.oit.dondok.infra.image.adapter;

import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.image.port.ImageObjectMetadata;
import com.oit.dondok.domain.image.port.ImageStoragePort;
import com.oit.dondok.domain.image.port.PresignedUpload;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// ImageStoragePort의 test 전용 stub. 실제 S3 호출 없이 테스트 컨텍스트를 부팅하기 위한 구현으로,
// test 외 모든 프로파일에서 등록되는 S3ImageStorageAdapter와 상호배타적이다.
@Component
@Profile("test")
public class StubImageStorageAdapter implements ImageStoragePort {

  private static final String UPLOAD_BASE_URL = "https://s3.example.com/upload";
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  @Override
  public PresignedUpload createPresignedUpload(
      ImageObjectKey key, String contentType, Duration ttl) {
    return new PresignedUpload(
        UPLOAD_BASE_URL + "/" + key.value(), key, OffsetDateTime.now(SEOUL).plus(ttl));
  }

  @Override
  public ImageObjectMetadata head(ImageObjectKey key) {
    return new ImageObjectMetadata(0L, "image/jpeg");
  }

  @Override
  public InputStream open(ImageObjectKey key) {
    return new ByteArrayInputStream(new byte[0]);
  }

  @Override
  public void put(ImageObjectKey key, byte[] bytes, String contentType) {}
}
