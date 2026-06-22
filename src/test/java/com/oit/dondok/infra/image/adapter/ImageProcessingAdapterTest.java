package com.oit.dondok.infra.image.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.junit.jupiter.params.provider.CsvSource;
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

  // 객체가 이미지가 아니면(디코딩 실패) IMAGE_DECODE_FAILED.
  @Test
  void reEncodeThrowsImageDecodeFailedWhenObjectIsNotImage() {
    given(imageStoragePort.open(any(ImageObjectKey.class)))
        .willReturn(stream("not-an-image".getBytes(StandardCharsets.UTF_8)));

    assertThatThrownBy(() -> imageProcessingAdapter.reEncode("mission/42/101/broken"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.IMAGE_DECODE_FAILED);

    verify(imageStoragePort, never()).put(any(), any(), any());
  }

  // 저장소 읽기 중 IOException은 디코딩 실패가 아니라 스토리지 실패(IMAGE_STORAGE_READ_FAILED)로 매핑된다.
  @Test
  void reEncodeThrowsStorageReadFailedWhenStorageReadErrors() {
    given(imageStoragePort.open(any(ImageObjectKey.class))).willReturn(failingStream());

    assertThatThrownBy(() -> imageProcessingAdapter.reEncode("mission/42/101/io"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.IMAGE_STORAGE_READ_FAILED);

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
        .validateHeaderDimensions(any(InputStream.class));

    assertThatThrownBy(() -> imageProcessingAdapter.reEncode("mission/42/101/huge-dim"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.IMAGE_DIMENSIONS_TOO_LARGE);

    verify(imageStoragePort, never()).put(any(), any(), any());
  }

  // EXIF Orientation 6/8(90/270도 회전)이면 결과 이미지의 가로·세로가 뒤바뀐다.
  @ParameterizedTest
  @ValueSource(ints = {6, 8})
  void reEncodeSwapsDimensionsForRotatedOrientation(int orientation) throws Exception {
    // 가로로 넓은 원본(width=20, height=10) -> 90/270도 회전 후 세로로 길어져야 한다.
    BufferedImage wide = new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB);
    given(imageStoragePort.open(any(ImageObjectKey.class)))
        .willReturn(stream(jpegWithOrientation(wide, orientation)));

    ArgumentCaptor<byte[]> out = ArgumentCaptor.forClass(byte[].class);
    imageProcessingAdapter.reEncode("mission/42/101/rot" + orientation);
    verify(imageStoragePort).put(any(), out.capture(), eq("image/jpeg"));

    BufferedImage result = ImageIO.read(new ByteArrayInputStream(out.getValue()));
    assertThat(result.getWidth()).isEqualTo(10);
    assertThat(result.getHeight()).isEqualTo(20);
  }

  // Orientation 1(정상)은 회전하지 않아 원본 치수를 유지한다.
  @Test
  void reEncodeKeepsDimensionsForNormalOrientation() throws Exception {
    BufferedImage wide = new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB);
    given(imageStoragePort.open(any(ImageObjectKey.class)))
        .willReturn(stream(jpegWithOrientation(wide, 1)));

    ArgumentCaptor<byte[]> out = ArgumentCaptor.forClass(byte[].class);
    imageProcessingAdapter.reEncode("mission/42/101/normal");
    verify(imageStoragePort).put(any(), out.capture(), eq("image/jpeg"));

    BufferedImage result = ImageIO.read(new ByteArrayInputStream(out.getValue()));
    assertThat(result.getWidth()).isEqualTo(20);
    assertThat(result.getHeight()).isEqualTo(10);
  }

  // Orientation 3(180도 회전)이면 좌상단 마커가 우하단으로 이동한다.
  @Test
  void reEncodeRotates180ForOrientation3() throws Exception {
    // 좌상단 사분면만 빨강, 나머지는 흰색.
    BufferedImage marked = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
    java.awt.Graphics2D g = marked.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, 40, 20);
    g.setColor(Color.RED);
    g.fillRect(0, 0, 20, 10);
    g.dispose();
    given(imageStoragePort.open(any(ImageObjectKey.class)))
        .willReturn(stream(jpegWithOrientation(marked, 3)));

    ArgumentCaptor<byte[]> out = ArgumentCaptor.forClass(byte[].class);
    imageProcessingAdapter.reEncode("mission/42/101/rot180");
    verify(imageStoragePort).put(any(), out.capture(), eq("image/jpeg"));

    BufferedImage result = ImageIO.read(new ByteArrayInputStream(out.getValue()));
    // 180도 회전 후 빨강은 우하단 사분면 중앙(30, 15)에 위치해야 한다.
    Color bottomRight = new Color(result.getRGB(30, 15));
    assertThat(bottomRight.getRed()).isGreaterThan(200);
    assertThat(bottomRight.getGreen()).isLessThan(80);
    assertThat(bottomRight.getBlue()).isLessThan(80);
    // 좌상단(10, 5)은 흰색으로 비워져 있어야 한다.
    Color topLeft = new Color(result.getRGB(10, 5));
    assertThat(topLeft.getRed()).isGreaterThan(200);
    assertThat(topLeft.getGreen()).isGreaterThan(200);
    assertThat(topLeft.getBlue()).isGreaterThan(200);
  }

  // 반전 포함 Orientation(2 좌우반전 / 4 상하반전 / 5 transpose / 7 transverse) 검증.
  // 좌상단 사분면 빨강 마커가 각 변환에 맞는 위치로 이동하고, 반대 지점은 흰색으로 비워진다.
  // 특히 5/7은 회전+반전이 합쳐진 패턴이라 좌표 매핑을 직접 고정해 회귀를 막는다.
  @ParameterizedTest
  @CsvSource({
    // orientation, redX, redY, whiteX, whiteY  (원본 W=40, H=20, 좌상단 마커 중심=(10,5))
    "2, 30, 5, 10, 5", // 좌우 반전 -> 마커가 우상단으로 (dest 40x20)
    "4, 10, 15, 10, 5", // 상하 반전 -> 마커가 좌하단으로 (dest 40x20)
    "5, 5, 10, 15, 30", // transpose: (x,y)->(y,x) (dest 20x40)
    "7, 15, 30, 5, 10" // transverse: (x,y)->(H-y,W-x) (dest 20x40)
  })
  void reEncodeAppliesFlipOrientations(int orientation, int redX, int redY, int whiteX, int whiteY)
      throws Exception {
    BufferedImage marked = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
    java.awt.Graphics2D g = marked.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, 40, 20);
    g.setColor(Color.RED);
    g.fillRect(0, 0, 20, 10); // 좌상단 사분면
    g.dispose();
    given(imageStoragePort.open(any(ImageObjectKey.class)))
        .willReturn(stream(jpegWithOrientation(marked, orientation)));

    ArgumentCaptor<byte[]> out = ArgumentCaptor.forClass(byte[].class);
    imageProcessingAdapter.reEncode("mission/42/101/flip" + orientation);
    verify(imageStoragePort).put(any(), out.capture(), eq("image/jpeg"));

    BufferedImage result = ImageIO.read(new ByteArrayInputStream(out.getValue()));
    Color red = new Color(result.getRGB(redX, redY));
    assertThat(red.getRed()).isGreaterThan(200);
    assertThat(red.getGreen()).isLessThan(80);
    assertThat(red.getBlue()).isLessThan(80);
    Color white = new Color(result.getRGB(whiteX, whiteY));
    assertThat(white.getRed()).isGreaterThan(200);
    assertThat(white.getGreen()).isGreaterThan(200);
    assertThat(white.getBlue()).isGreaterThan(200);
  }

  // 베이스라인 JPEG에 EXIF APP1(Orientation 태그)을 삽입해, 회전 보정 입력을 만든다.
  private static byte[] jpegWithOrientation(BufferedImage image, int orientation)
      throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(image, "jpg", baos);
    byte[] jpeg = baos.toByteArray();
    byte[] app1 = exifApp1(orientation);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(jpeg, 0, 2); // SOI (FFD8)
    out.write(app1); // EXIF APP1 세그먼트
    out.write(jpeg, 2, jpeg.length - 2); // 이후 원본 세그먼트
    return out.toByteArray();
  }

  // Orientation 태그 하나만 담은 최소 EXIF APP1 세그먼트(big-endian TIFF).
  private static byte[] exifApp1(int orientation) {
    byte[] tiff = {
      'M',
      'M', // big-endian
      0x00,
      0x2A, // TIFF magic
      0x00,
      0x00,
      0x00,
      0x08, // IFD0 offset
      0x00,
      0x01, // 엔트리 1개
      0x01,
      0x12, // 태그: Orientation
      0x00,
      0x03, // 타입: SHORT
      0x00,
      0x00,
      0x00,
      0x01, // count 1
      (byte) ((orientation >> 8) & 0xFF),
      (byte) (orientation & 0xFF),
      0x00,
      0x00, // 값
      0x00,
      0x00,
      0x00,
      0x00 // 다음 IFD 없음
    };
    byte[] exifHeader = {'E', 'x', 'i', 'f', 0x00, 0x00};
    int length = exifHeader.length + tiff.length + 2; // length 필드 자신 2바이트 포함

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(0xFF);
    out.write(0xE1); // APP1 marker
    out.write((length >> 8) & 0xFF);
    out.write(length & 0xFF);
    out.writeBytes(exifHeader);
    out.writeBytes(tiff);
    return out.toByteArray();
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
