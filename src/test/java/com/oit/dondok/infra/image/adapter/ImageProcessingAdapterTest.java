package com.oit.dondok.infra.image.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.image.port.ImageStoragePort;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.infra.image.exception.ImageErrorCode;
import com.oit.dondok.infra.image.service.ImageObjectValidator;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImageProcessingAdapterTest {

  @Mock private ImageStoragePort imageStoragePort;
  @Mock private ImageObjectValidator imageObjectValidator;

  @InjectMocks private ImageProcessingAdapter imageProcessingAdapter;

  // 재인코딩된 JPG가 같은 key/contentType으로 다시 업로드된다.
  @Test
  void reEncodeReuploadsReEncodedJpegToSameKey() throws Exception {
    given(imageStoragePort.open(any(ImageObjectKey.class))).willReturn(stream(sampleImageBytes()));

    imageProcessingAdapter.reEncode("mission/42/101/abc");

    verify(imageStoragePort)
        .put(eq(new ImageObjectKey("mission/42/101/abc")), any(byte[].class), eq("image/jpeg"));
  }

  // 객체가 이미지가 아니면 IMAGE_READ_FAILED.
  @Test
  void reEncodeThrowsImageReadFailedWhenObjectIsNotImage() {
    given(imageStoragePort.open(any(ImageObjectKey.class)))
        .willReturn(stream("not-an-image".getBytes(StandardCharsets.UTF_8)));

    assertThatThrownBy(() -> imageProcessingAdapter.reEncode("mission/42/101/broken"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.IMAGE_READ_FAILED);

    verify(imageStoragePort, never()).put(any(), any(), any());
  }

  // 저장소 읽기 중 IOException은 인코딩 실패가 아니라 IMAGE_READ_FAILED로 매핑된다.
  @Test
  void reEncodeThrowsImageReadFailedWhenStorageReadErrors() {
    given(imageStoragePort.open(any(ImageObjectKey.class))).willReturn(failingStream());

    assertThatThrownBy(() -> imageProcessingAdapter.reEncode("mission/42/101/io"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.IMAGE_READ_FAILED);

    verify(imageStoragePort, never()).put(any(), any(), any());
  }

  // 검증(존재 여부)에서 NOT_FOUND가 나면 다운로드/재업로드로 진행하지 않고 전파한다.
  @Test
  void reEncodePropagatesNotFoundFromValidatorWithoutOpening() {
    given(imageObjectValidator.validate(any(ImageObjectKey.class)))
        .willThrow(new CustomException(ImageErrorCode.IMAGE_NOT_FOUND));

    assertThatThrownBy(() -> imageProcessingAdapter.reEncode("mission/42/101/missing"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.IMAGE_NOT_FOUND);

    verify(imageStoragePort, never()).open(any(ImageObjectKey.class));
  }

  // 검증에서 TOO_LARGE가 나면 열지 않고 전파한다.
  @Test
  void reEncodePropagatesTooLargeFromValidatorWithoutOpening() {
    given(imageObjectValidator.validate(any(ImageObjectKey.class)))
        .willThrow(new CustomException(ImageErrorCode.IMAGE_TOO_LARGE));

    assertThatThrownBy(() -> imageProcessingAdapter.reEncode("mission/42/101/huge"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.IMAGE_TOO_LARGE);

    verify(imageStoragePort, never()).open(any(ImageObjectKey.class));
  }

  // 검증에서 UNSUPPORTED_IMAGE_TYPE이 나면 열지 않고 전파한다.
  @Test
  void reEncodePropagatesUnsupportedTypeFromValidatorWithoutOpening() {
    given(imageObjectValidator.validate(any(ImageObjectKey.class)))
        .willThrow(new CustomException(ImageErrorCode.UNSUPPORTED_IMAGE_TYPE));

    assertThatThrownBy(() -> imageProcessingAdapter.reEncode("mission/42/101/gif"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.UNSUPPORTED_IMAGE_TYPE);

    verify(imageStoragePort, never()).open(any(ImageObjectKey.class));
  }

  // 재인코딩 결과가 한도를 넘으면 같은 key에 저장하지 않는다 (디컴프레션 팽창 방어).
  @Test
  void reEncodeRejectsReEncodedOutputExceedingMax() throws Exception {
    given(imageStoragePort.open(any(ImageObjectKey.class))).willReturn(stream(sampleImageBytes()));
    willThrow(new CustomException(ImageErrorCode.IMAGE_TOO_LARGE))
        .given(imageObjectValidator)
        .validateContentPolicy(eq("image/jpeg"), anyLong());

    assertThatThrownBy(() -> imageProcessingAdapter.reEncode("mission/42/101/big"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.IMAGE_TOO_LARGE);

    verify(imageStoragePort, never()).put(any(), any(), any());
  }

  // PNG/GIF/BMP 원본은 JPEG로 변환되어 같은 key/contentType으로 업로드된다.
  @ParameterizedTest
  @ValueSource(strings = {"png", "gif", "bmp"})
  void reEncodeConvertsFormatsToJpeg(String format) throws Exception {
    BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream src = new ByteArrayOutputStream();
    ImageIO.write(img, format, src);
    given(imageStoragePort.open(any(ImageObjectKey.class))).willReturn(stream(src.toByteArray()));

    imageProcessingAdapter.reEncode("mission/42/101/" + format);

    verify(imageStoragePort).put(any(), any(byte[].class), eq("image/jpeg"));
  }

  // 알파(투명) 픽셀은 JPEG 변환 시 흰 배경으로 평탄화된다.
  @Test
  void reEncodeFlattensAlphaToWhite() throws Exception {
    BufferedImage argb = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
    argb.setRGB(0, 0, 0x00000000); // 완전 투명
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    ImageIO.write(argb, "png", png);
    given(imageStoragePort.open(any(ImageObjectKey.class))).willReturn(stream(png.toByteArray()));

    ArgumentCaptor<byte[]> out = ArgumentCaptor.forClass(byte[].class);
    imageProcessingAdapter.reEncode("mission/42/101/alpha");
    verify(imageStoragePort).put(any(), out.capture(), eq("image/jpeg"));

    BufferedImage jpeg = ImageIO.read(new ByteArrayInputStream(out.getValue()));
    Color pixel = new Color(jpeg.getRGB(0, 0));
    // 투명 영역이 흰 배경으로 채워진다(JPEG 손실 감안한 근사).
    assertThat(pixel.getRed()).isGreaterThan(240);
    assertThat(pixel.getGreen()).isGreaterThan(240);
    assertThat(pixel.getBlue()).isGreaterThan(240);
  }

  // WEBP 디코더가 ImageIO SPI에 등록되어 있어야 한다(imageio-webp 의존성).
  @Test
  void webpReaderIsRegistered() {
    assertThat(ImageIO.getImageReadersByFormatName("webp").hasNext()).isTrue();
  }

  // 치수가 한도를 넘으면 디코딩/업로드 전에 차단된다(decompression bomb 방어).
  @Test
  void reEncodeRejectsOversizedDimensionsBeforeUpload() throws Exception {
    given(imageStoragePort.open(any(ImageObjectKey.class))).willReturn(stream(sampleImageBytes()));
    willThrow(new CustomException(ImageErrorCode.IMAGE_DIMENSIONS_TOO_LARGE))
        .given(imageObjectValidator)
        .validateDimensions(anyInt(), anyInt());

    assertThatThrownBy(() -> imageProcessingAdapter.reEncode("mission/42/101/huge-dim"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.IMAGE_DIMENSIONS_TOO_LARGE);

    verify(imageStoragePort, never()).put(any(), any(), any());
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

  private static byte[] sampleImageBytes() throws IOException {
    BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    ImageIO.write(image, "png", os);
    return os.toByteArray();
  }
}
