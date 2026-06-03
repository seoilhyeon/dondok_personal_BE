package com.oit.dondok.infra.image.service;

import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.image.port.ImageObjectMetadata;
import com.oit.dondok.domain.image.port.ImageStoragePort;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.infra.image.exception.ImageErrorCode;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// S3 object의 존재/크기/타입을 한 곳에서 검증한다.
// 추출(extract), re-encode, mission-log가 동일 정책을 공유해 size/content-type drift를 제거한다.
@Component
@RequiredArgsConstructor
public class ImageObjectValidator {
  // size/content-type 정책의 단일 출처
  private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg");
  private static final long MAX_CONTENT_LENGTH = 10 * 1024 * 1024; // 10MB

  private final ImageStoragePort imageStoragePort;

  // 존재(head 실패 -> 어댑터가 IMAGE_NOT_FOUND로 매핑) + 크기 + 타입을 검증하고 메타데이터를 반환한다.
  public ImageObjectMetadata validate(ImageObjectKey key) {
    ImageObjectMetadata metadata = imageStoragePort.head(key);
    validateContentPolicy(metadata.contentType(), metadata.contentLength());
    return metadata;
  }

  // 형식/크기 정책의 단일 출처. presign 요청(신고값)·head 메타데이터·재인코딩 결과가 모두 이 한 곳을 통과한다.
  public void validateContentPolicy(String contentType, long contentLength) {
    if (contentLength > MAX_CONTENT_LENGTH) {
      throw new CustomException(ImageErrorCode.IMAGE_TOO_LARGE);
    }
    if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
      throw new CustomException(ImageErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }
  }
}
