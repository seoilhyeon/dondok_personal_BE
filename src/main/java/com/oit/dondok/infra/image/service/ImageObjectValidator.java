package com.oit.dondok.infra.image.service;

import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.image.port.ImageObjectMetadata;
import com.oit.dondok.domain.image.port.ImageStoragePort;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.infra.image.exception.ImageErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// S3 object의 존재/크기/타입을 한 곳에서 검증한다.
// 추출(extract), re-encode, mission-log가 동일 정책을 공유해 size/content-type drift를 제거한다.
@Component
@RequiredArgsConstructor
public class ImageObjectValidator {
  // size/content-type 정책의 단일 출처
  private static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of("image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp");
  private static final long MAX_CONTENT_LENGTH = 10 * 1024 * 1024; // 10MB
  private static final long MAX_PIXELS = 50L * 1_000_000; // 50MP (decompression bomb 방어)
  private static final int MAX_DIMENSION = 10_000; // 변당 최대 px

  private final ImageStoragePort imageStoragePort;

  // 존재(head 실패 -> 어댑터가 IMAGE_NOT_FOUND로 매핑) + 크기 + 타입을 검증하고 메타데이터를 반환한다.
  public ImageObjectMetadata validate(ImageObjectKey key) {
    ImageObjectMetadata metadata = imageStoragePort.head(key);
    validateContentPolicy(metadata.contentType(), metadata.contentLength());
    return metadata;
  }

  // 입력 스트림의 헤더만 파싱해(라스터 미할당) 치수를 검증한다. extract/re-encode가 공유하는 단일 출처.
  // 디코딩 불가(빈/손상/미지원 reader)는 IMAGE_READ_FAILED로 매핑한다.
  public void validateDimensions(InputStream in) {
    try (ImageInputStream imageStream = ImageIO.createImageInputStream(in)) {
      if (imageStream == null) {
        throw new CustomException(ImageErrorCode.IMAGE_READ_FAILED);
      }
      Iterator<ImageReader> readers = ImageIO.getImageReaders(imageStream);
      if (!readers.hasNext()) {
        throw new CustomException(ImageErrorCode.IMAGE_READ_FAILED);
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(imageStream, true, true); // seekForwardOnly, ignoreMetadata
        validateDimensions(reader.getWidth(0), reader.getHeight(0));
      } finally {
        reader.dispose();
      }
    } catch (IOException e) {
      throw new CustomException(ImageErrorCode.IMAGE_READ_FAILED);
    }
  }

  // 풀 디코딩(라스터 할당) 전에 헤더 치수로 검증한다. 작은 파일이 거대 해상도로 디코딩되는 OOM을 막는다.
  public void validateDimensions(int width, int height) {
    if (width <= 0 || height <= 0) {
      throw new CustomException(ImageErrorCode.IMAGE_READ_FAILED);
    }
    if (width > MAX_DIMENSION || height > MAX_DIMENSION || (long) width * height > MAX_PIXELS) {
      throw new CustomException(ImageErrorCode.IMAGE_DIMENSIONS_TOO_LARGE);
    }
  }

  // 형식/크기 정책의 단일 출처. presign 요청(신고값)·head 메타데이터·재인코딩 결과가 모두 이 한 곳을 통과한다.
  public void validateContentPolicy(String contentType, long contentLength) {
    if (contentLength <= 0) {
      throw new CustomException(ImageErrorCode.EMPTY_IMAGE);
    }
    if (contentLength > MAX_CONTENT_LENGTH) {
      throw new CustomException(ImageErrorCode.IMAGE_TOO_LARGE);
    }
    if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
      throw new CustomException(ImageErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }
  }
}
