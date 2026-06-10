package com.oit.dondok.infra.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.image.port.ImageObjectMetadata;
import com.oit.dondok.domain.image.port.ImageStoragePort;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.infra.image.exception.ImageErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImageObjectValidatorTest {

  private static final long MAX_CONTENT_LENGTH = 10L * 1024 * 1024; // 10MB
  private static final ImageObjectKey KEY = new ImageObjectKey("mission/1/1/file");

  @Mock private ImageStoragePort imageStoragePort;

  @InjectMocks private ImageObjectValidator validator;

  @Test
  void validateReturnsMetadataWhenWithinPolicy() {
    ImageObjectMetadata metadata = new ImageObjectMetadata(2048L, "image/jpeg");
    given(imageStoragePort.head(KEY)).willReturn(metadata);

    assertThat(validator.validate(KEY)).isEqualTo(metadata);
  }

  @Test
  void validateAllowsExactlyMaxContentLength() {
    // 경계값(정확히 한도)은 통과해야 한다.
    given(imageStoragePort.head(KEY))
        .willReturn(new ImageObjectMetadata(MAX_CONTENT_LENGTH, "image/jpeg"));

    assertThatCode(() -> validator.validate(KEY)).doesNotThrowAnyException();
  }

  @Test
  void validateThrowsTooLargeWhenContentLengthExceedsMax() {
    given(imageStoragePort.head(KEY))
        .willReturn(new ImageObjectMetadata(MAX_CONTENT_LENGTH + 1, "image/jpeg"));

    assertThatThrownBy(() -> validator.validate(KEY))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.IMAGE_TOO_LARGE);
  }

  @Test
  void validateThrowsUnsupportedWhenContentTypeNotAllowed() {
    given(imageStoragePort.head(KEY)).willReturn(new ImageObjectMetadata(2048L, "image/heic"));

    assertThatThrownBy(() -> validator.validate(KEY))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.UNSUPPORTED_IMAGE_TYPE);
  }

  @Test
  void validateChecksSizeBeforeType() {
    // 크기·타입이 모두 위반이면 크기 초과가 먼저 보고된다.
    given(imageStoragePort.head(KEY))
        .willReturn(new ImageObjectMetadata(MAX_CONTENT_LENGTH + 1, "image/heic"));

    assertThatThrownBy(() -> validator.validate(KEY))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.IMAGE_TOO_LARGE);
  }

  @Test
  void validatePropagatesNotFoundFromHead() {
    // 객체 부재는 포트(head)가 IMAGE_NOT_FOUND로 매핑하며, 검증기는 그대로 전파한다.
    given(imageStoragePort.head(any(ImageObjectKey.class)))
        .willThrow(new CustomException(ImageErrorCode.IMAGE_NOT_FOUND));

    assertThatThrownBy(() -> validator.validate(KEY))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.IMAGE_NOT_FOUND);
  }

  // validateContentPolicy는 head 없이 presign 요청·재인코딩 결과 검증에도 직접 쓰인다.
  @Test
  void validateContentPolicyAllowsExactlyMaxContentLength() {
    assertThatCode(() -> validator.validateContentPolicy("image/jpeg", MAX_CONTENT_LENGTH))
        .doesNotThrowAnyException();
  }

  @Test
  void validateContentPolicyThrowsTooLargeWhenExceedsMax() {
    assertThatThrownBy(() -> validator.validateContentPolicy("image/jpeg", MAX_CONTENT_LENGTH + 1))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.IMAGE_TOO_LARGE);
  }

  @Test
  void validateContentPolicyThrowsUnsupportedWhenTypeNotAllowed() {
    assertThatThrownBy(() -> validator.validateContentPolicy("image/heic", 2048L))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.UNSUPPORTED_IMAGE_TYPE);
  }

  // 신규 허용 포맷(JPEG 외 PNG/GIF/BMP/WEBP)은 정책을 통과한다.
  @ParameterizedTest
  @ValueSource(strings = {"image/png", "image/gif", "image/bmp", "image/webp"})
  void validateContentPolicyAllowsNewImageTypes(String contentType) {
    assertThatCode(() -> validator.validateContentPolicy(contentType, 2048L))
        .doesNotThrowAnyException();
  }

  // 지원하지 않는 타입(HEIC 등)은 거부한다.
  @ParameterizedTest
  @ValueSource(strings = {"image/heic", "image/heif", "image/tiff", "application/pdf"})
  void validateContentPolicyRejectsUnsupportedTypes(String contentType) {
    assertThatThrownBy(() -> validator.validateContentPolicy(contentType, 2048L))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.UNSUPPORTED_IMAGE_TYPE);
  }
}
