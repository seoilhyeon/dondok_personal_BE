package com.oit.dondok.infra.image.adapter;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.oit.dondok.domain.mission.port.ImageMetadata;
import com.oit.dondok.domain.mission.port.ImageMetadataPort;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import com.oit.dondok.infra.image.exception.ImageErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HexFormat;
import java.util.TimeZone;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

// ImageMetadataPort의 S3 구현. 원본 객체 바이트를 1회만 내려받아 같은 byte[]에서 EXIF 촬영 시각과 SHA-256을 함께 계산한다.
// (S3 GET을 두 번 하지 않는다.)
@Component
@RequiredArgsConstructor
public class S3ImageMetadataAdapter implements ImageMetadataPort {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final long MAX_CONTENT_LENGTH = 10 * 1024 * 1024; // 10MB

  private final S3Client s3Client;

  @Value("${app.aws.s3.bucket}")
  private String bucket;

  @Override
  public ImageMetadata extract(String s3Key) {
    byte[] bytes = downloadBytes(s3Key);
    return new ImageMetadata(extractTakenAt(bytes), sha256Hex(bytes));
  }

  private byte[] downloadBytes(String s3Key) {
    // 전체 바이트를 메모리에 적재하기 전에 HeadObject로 크기를 먼저 검증한다 (OOM/DoS 방어).
    verifyObjectSize(s3Key);
    try {
      return s3Client
          .getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(s3Key).build())
          .asByteArray();
    } catch (NoSuchKeyException e) {
      throw new CustomException(ImageErrorCode.IMAGE_NOT_FOUND);
    }
  }

  private void verifyObjectSize(String s3Key) {
    HeadObjectResponse head;
    try {
      head = s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(s3Key).build());
    } catch (NoSuchKeyException e) {
      throw new CustomException(ImageErrorCode.IMAGE_NOT_FOUND);
    }
    if (head.contentLength() != null && head.contentLength() > MAX_CONTENT_LENGTH) {
      throw new CustomException(ImageErrorCode.IMAGE_TOO_LARGE);
    }
  }

  // EXIF DateTimeOriginal을 "촬영 절대 시각"으로 해석해 Asia/Seoul offset으로 표현한다.
  // getDateOriginal(TimeZone): EXIF에 offset 태그가 있으면 그 offset으로 절대 시각(instant)을 계산하고,
  // 없으면 전달한 SEOUL을 폴백으로 사용한다. 즉 EXIF의 로컬 벽시계를 그대로 보존하는 게 아니라,
  // 촬영 절대 시각을 KST로 환산해 보관한다. (미션 인정 윈도우/ server_time과 절대 시각 기준으로 비교하기 위함)
  // EXIF가 없거나 파싱 실패 시 null을 반환한다 (signal만 다루므로 예외를 던지지 않는다).
  private OffsetDateTime extractTakenAt(byte[] bytes) {
    try {
      Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
      ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
      if (directory == null) {
        return null;
      }
      Date takenAt = directory.getDateOriginal(TimeZone.getTimeZone(SEOUL));
      return takenAt == null ? null : OffsetDateTime.ofInstant(takenAt.toInstant(), SEOUL);
    } catch (ImageProcessingException | IOException e) {
      return null;
    }
  }

  private String sha256Hex(byte[] bytes) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256은 표준 JDK에 항상 존재하므로 사실상 도달 불가.
      throw new CustomException(GlobalErrorCode.SERVER_ERROR, e);
    }
  }
}
