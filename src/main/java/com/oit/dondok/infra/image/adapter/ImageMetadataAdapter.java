package com.oit.dondok.infra.image.adapter;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.image.port.ImageStoragePort;
import com.oit.dondok.domain.mission.port.ImageMetadata;
import com.oit.dondok.domain.mission.port.ImageMetadataPort;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import com.oit.dondok.infra.image.exception.ImageErrorCode;
import com.oit.dondok.infra.image.service.ImageObjectValidator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HexFormat;
import java.util.TimeZone;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// ImageMetadataPort 구현. 원본 객체 바이트를 ImageStoragePort.open()으로 1회만 읽어
// 같은 byte[]에서 EXIF 촬영 시각과 SHA-256을 함께 계산한다 (스토리지 GET을 두 번 하지 않는다).
@Component
@RequiredArgsConstructor
public class ImageMetadataAdapter implements ImageMetadataPort {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final ImageStoragePort imageStoragePort;
  private final ImageObjectValidator imageObjectValidator;

  @Override
  public ImageMetadata extract(String s3Key) {
    ImageObjectKey key = new ImageObjectKey(s3Key);
    // 바이트를 내려받기 전에 존재/크기/타입을 공통 정책으로 선검증한다 (OOM/DoS 방어).
    imageObjectValidator.validate(key);

    byte[] bytes = readAllBytes(key);
    // 풀 디코딩 전에 헤더 치수로 검증해, 거대 해상도 이미지를 제출 시점에 동기 거절한다(decompression bomb 방어).
    imageObjectValidator.validateDimensions(new ByteArrayInputStream(bytes));
    return new ImageMetadata(extractTakenAt(bytes), sha256Hex(bytes));
  }

  // open() 스트림에서 전체 바이트를 읽는다. 객체 부재(NoSuchKey)는 포트가 IMAGE_NOT_FOUND로 매핑한다.
  private byte[] readAllBytes(ImageObjectKey key) {
    try (InputStream inputStream = imageStoragePort.open(key)) {
      return inputStream.readAllBytes();
    } catch (IOException e) {
      throw new CustomException(ImageErrorCode.IMAGE_READ_FAILED);
    }
  }

  // EXIF DateTimeOriginal을 "촬영 절대 시각"으로 해석해 Asia/Seoul offset으로 표현한다.
  // getDateOriginal(TimeZone): EXIF에 offset 태그가 있으면 그 offset으로 절대 시각(instant)을 계산하고,
  // 없으면 전달한 SEOUL을 폴백으로 사용한다. 즉 EXIF의 로컬 벽시계를 그대로 보존하는 게 아니라, 촬영 절대 시각을 KST로 환산해 보관한다.
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
