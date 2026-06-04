package com.oit.dondok.infra.image.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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

  // SHA-256("") 표준 테스트 벡터.
  private static final String EMPTY_SHA256 =
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

  @Mock private ImageStoragePort imageStoragePort;

  @Mock private ImageObjectValidator imageObjectValidator;

  @InjectMocks private ImageMetadataAdapter adapter;

  // 빈 바이트의 SHA-256은 표준 벡터와 일치하고, EXIF가 없으면 takenAt은 null이다.
  @Test
  void extractComputesSha256AndReturnsNullTakenAtWhenNoExif() {
    given(imageStoragePort.open(any(ImageObjectKey.class))).willReturn(stream(new byte[0]));

    ImageMetadata metadata = adapter.extract("mission/1/1/empty");

    assertThat(metadata.sha256()).isEqualTo(EMPTY_SHA256);
    assertThat(metadata.takenAt()).isNull();
  }

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
