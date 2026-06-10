package com.oit.dondok.infra.image.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.image.port.ImageStoragePort;
import com.oit.dondok.domain.mission.port.ImageMetadata;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.infra.image.exception.ImageErrorCode;
import com.oit.dondok.infra.image.service.ImageObjectValidator;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImageMetadataAdapterTest {

  @Mock private ImageStoragePort imageStoragePort;

  @Mock private ImageObjectValidator imageObjectValidator;

  @InjectMocks private ImageMetadataAdapter adapter;

  // EXIF가 없는 정상 jpeg는 takenAt이 null이고 해시는 64자 hex로 계산된다.
  @Test
  void extractReturnsNullTakenAtForImageWithoutExif() throws Exception {
    given(imageStoragePort.open(any(ImageObjectKey.class)))
        .willReturn(stream(jpegBytesWithoutExif()));

    ImageMetadata metadata = adapter.extract("mission/1/1/plain");

    assertThat(metadata.takenAt()).isNull();
    assertThat(metadata.sha256()).matches("[0-9a-f]{64}");
  }

  // 검증기가 부재(IMAGE_NOT_FOUND)로 막으면 바이트를 읽지 않고 그대로 전파한다.
  @Test
  void extractPropagatesNotFoundFromValidatorWithoutReadingBytes() {
    given(imageObjectValidator.validate(any(ImageObjectKey.class)))
        .willThrow(new CustomException(ImageErrorCode.IMAGE_NOT_FOUND));

    assertThatThrownBy(() -> adapter.extract("mission/1/1/missing"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.IMAGE_NOT_FOUND);

    verify(imageStoragePort, never()).open(any(ImageObjectKey.class));
  }

  // 검증기가 크기 초과(IMAGE_TOO_LARGE)로 막으면 다운로드 전에 차단된다.
  @Test
  void extractPropagatesTooLargeFromValidatorWithoutReadingBytes() {
    given(imageObjectValidator.validate(any(ImageObjectKey.class)))
        .willThrow(new CustomException(ImageErrorCode.IMAGE_TOO_LARGE));

    assertThatThrownBy(() -> adapter.extract("mission/1/1/huge"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.IMAGE_TOO_LARGE);

    verify(imageStoragePort, never()).open(any(ImageObjectKey.class));
  }

  // 스트림 읽기 중 IOException은 IMAGE_READ_FAILED로 변환된다.
  @Test
  void extractThrowsReadFailedWhenStreamErrors() {
    given(imageStoragePort.open(any(ImageObjectKey.class))).willReturn(failingStream());

    assertThatThrownBy(() -> adapter.extract("mission/1/1/broken"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.IMAGE_READ_FAILED);
  }

  // 헤더 치수가 한도를 넘으면 제출 시점에 IMAGE_DIMENSIONS_TOO_LARGE로 동기 거절된다.
  @Test
  void extractRejectsOversizedDimensions() throws Exception {
    given(imageStoragePort.open(any(ImageObjectKey.class)))
        .willReturn(stream(jpegBytesWithoutExif()));
    willThrow(new CustomException(ImageErrorCode.IMAGE_DIMENSIONS_TOO_LARGE))
        .given(imageObjectValidator)
        .validateHeaderDimensions(any(InputStream.class));

    assertThatThrownBy(() -> adapter.extract("mission/1/1/huge-dim"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.IMAGE_DIMENSIONS_TOO_LARGE);
  }

  private static InputStream stream(byte[] bytes) {
    return new ByteArrayInputStream(bytes);
  }

  private static InputStream failingStream() {
    return new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("stream error");
      }
    };
  }

  private static byte[] jpegBytesWithoutExif() throws IOException {
    BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    ImageIO.write(image, "jpg", os);
    return os.toByteArray();
  }
}
