package com.oit.dondok.infra.image.adapter;

import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.image.port.ImageObjectMetadata;
import com.oit.dondok.domain.image.port.ImageStoragePort;
import com.oit.dondok.domain.image.port.PresignedUpload;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import com.oit.dondok.infra.image.exception.ImageErrorCode;
import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

// ImageStoragePort의 실제 S3 구현. StubImageStorageAdapter(test 전용)와 상호배타적으로
// test를 제외한 모든 프로파일(local/dev/prod/integration)에서 등록된다.
// AWS SDK 타입은 이 어댑터 내부에만 머물고, domain port에는 노출되지 않는다.
@Component
@Profile("!test")
@RequiredArgsConstructor
public class S3ImageStorageAdapter implements ImageStoragePort {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final S3Presigner s3Presigner;
  private final S3Client s3Client;

  @Value("${app.aws.s3.bucket}")
  private String bucket;

  // S3 PUT을 허용하는 서명된 URL을 발급한다. 만료 정책(ttl)은 호출자(앱 레이어)가 결정한다.
  @Override
  public PresignedUpload createPresignedUpload(
      ImageObjectKey key, String contentType, Duration ttl) {
    PutObjectPresignRequest presignRequest =
        PutObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .putObjectRequest(r -> r.bucket(bucket).key(key.value()).contentType(contentType))
            .build();

    String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();
    OffsetDateTime expiresAt = OffsetDateTime.now(SEOUL).plus(ttl);
    return new PresignedUpload(uploadUrl, key, expiresAt);
  }

  // HeadObject로 본문 없이 메타데이터만 조회한다.
  @Override
  public ImageObjectMetadata head(ImageObjectKey key) {
    try {
      HeadObjectResponse head =
          s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key.value()).build());
      long contentLength = head.contentLength() == null ? 0L : head.contentLength();
      return new ImageObjectMetadata(contentLength, head.contentType());
    } catch (SdkException e) {
      throw toReadException(e);
    }
  }

  @Override
  public InputStream open(ImageObjectKey key) {
    try {
      return s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key.value()).build());
    } catch (SdkException e) {
      throw toReadException(e);
    }
  }

  @Override
  public void put(ImageObjectKey key, byte[] bytes, String contentType) {
    try {
      s3Client.putObject(
          PutObjectRequest.builder()
              .bucket(bucket)
              .key(key.value())
              .contentType(contentType)
              .build(),
          RequestBody.fromBytes(bytes));
    } catch (SdkException e) {
      // 쓰기 실패는 "이미지 없음"이 아니다(404=버킷 부재 등 설정 오류 포함). 원인 보존 후 SERVER_ERROR로 매핑.
      throw new CustomException(GlobalErrorCode.SERVER_ERROR, e);
    }
  }

  // 읽기 경로 전용 매핑: 404(NoSuchKey 또는 본문 없는 HeadObject 404) -> IMAGE_NOT_FOUND, 그 외 -> SERVER_ERROR
  private CustomException toReadException(SdkException e) {
    if (e instanceof NoSuchKeyException
        || (e instanceof S3Exception s3 && s3.statusCode() == 404)) {
      return new CustomException(ImageErrorCode.IMAGE_NOT_FOUND);
    }
    return new CustomException(GlobalErrorCode.SERVER_ERROR, e);
  }
}
