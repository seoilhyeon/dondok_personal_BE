package com.oit.dondok.infra.image.adapter;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.image.port.ImageStoragePort;
import com.oit.dondok.domain.mission.port.ImageProcessingPort;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.infra.image.exception.ImageErrorCode;
import com.oit.dondok.infra.image.service.ImageObjectValidator;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// ImageProcessingPort 구현: 원본을 디코딩 -> EXIF Orientation 보정 -> JPG 재인코딩(EXIF 제거) -> 같은 key에 덮어쓴다.
@Component
@RequiredArgsConstructor
public class ImageProcessingAdapter implements ImageProcessingPort {

  // EXIF Orientation 태그가 없거나 정상(=픽셀이 이미 똑바로 선 상태)일 때의 값.
  private static final int ORIENTATION_NORMAL = 1;
  // 90/270도 회전이 적용되어 가로·세로가 뒤바뀌는 Orientation 값의 시작 지점(5~8).
  private static final int ORIENTATION_DIMENSION_SWAP_THRESHOLD = 5;

  private final ImageStoragePort imageStoragePort;
  private final ImageObjectValidator imageObjectValidator;

  @Override
  public void reEncode(String s3Key) {
    ImageObjectKey key = new ImageObjectKey(s3Key);
    // 다운로드/디코딩 전 존재/크기/타입 선검증 (없으면 어댑터가 IMAGE_NOT_FOUND 매핑)
    imageObjectValidator.validate(key);

    byte[] original = readAllBytes(key);
    // EXIF는 재인코딩 시 제거되므로, 제거 전에 Orientation을 읽어 픽셀을 실제로 똑바로 세운다.
    int orientation = readExifOrientation(original);
    BufferedImage image = decodeWithinLimits(original);
    BufferedImage oriented = applyOrientation(image, orientation);
    byte[] reEncoded = encodeJpeg(oriented);

    // 재인코딩 결과도 동일 정책으로 재검증(거대 픽셀 원본이 한도 초과 JPEG로 팽창하는 경우 차단)
    imageObjectValidator.validateContentPolicy("image/jpeg", reEncoded.length);
    imageStoragePort.put(key, reEncoded, "image/jpeg");
  }

  // EXIF Orientation(1~8)을 읽는다. EXIF/태그가 없거나 파싱에 실패하면 보정 불필요인 1로 폴백한다.
  // 스마트폰 사진은 픽셀을 돌리지 않고 이 태그로 회전을 지시하므로, EXIF 제거 전에 값을 확보해야 한다.
  private int readExifOrientation(byte[] bytes) {
    try {
      Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
      ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
      if (directory == null || !directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
        return ORIENTATION_NORMAL;
      }
      return directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
    } catch (ImageProcessingException | IOException | MetadataException e) {
      return ORIENTATION_NORMAL;
    }
  }

  // EXIF Orientation 값에 따라 픽셀을 실제로 회전/반전해 똑바로 세운다.
  // 직교(90도 단위) 변환만 발생하므로 NEAREST_NEIGHBOR로 보간 손실 없이 처리한다.
  private BufferedImage applyOrientation(BufferedImage image, int orientation) {
    if (orientation <= ORIENTATION_NORMAL || orientation > 8) {
      // 1(정상) 또는 알 수 없는 값은 원본 그대로 둔다(과회전 방지).
      return image;
    }
    int width = image.getWidth();
    int height = image.getHeight();
    // 5~8은 90/270도 회전이 포함되어 결과 이미지의 가로·세로가 뒤바뀐다.
    boolean swapDimensions = orientation >= ORIENTATION_DIMENSION_SWAP_THRESHOLD;
    int targetWidth = swapDimensions ? height : width;
    int targetHeight = swapDimensions ? width : height;

    AffineTransform transform = orientationTransform(orientation, width, height);
    AffineTransformOp op =
        new AffineTransformOp(transform, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
    BufferedImage dest = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
    op.filter(image, dest);
    return dest;
  }

  // EXIF Orientation(2~8) -> 결과 좌표계로 매핑하는 AffineTransform.
  private AffineTransform orientationTransform(int orientation, int width, int height) {
    AffineTransform transform = new AffineTransform();
    switch (orientation) {
      case 2 -> { // 좌우 반전
        transform.scale(-1.0, 1.0);
        transform.translate(-width, 0);
      }
      case 3 -> { // 180도 회전
        transform.translate(width, height);
        transform.rotate(Math.PI);
      }
      case 4 -> { // 상하 반전
        transform.scale(1.0, -1.0);
        transform.translate(0, -height);
      }
      case 5 -> { // 좌우 반전 + 90도 회전(transpose)
        transform.rotate(-Math.PI / 2);
        transform.scale(-1.0, 1.0);
      }
      case 6 -> { // 시계방향 90도 회전
        transform.translate(height, 0);
        transform.rotate(Math.PI / 2);
      }
      case 7 -> { // 좌우 반전 + 270도 회전(transverse)
        transform.scale(-1.0, 1.0);
        transform.translate(-height, 0);
        transform.translate(0, width);
        transform.rotate(3 * Math.PI / 2);
      }
      case 8 -> { // 반시계방향 90도 회전(270도 시계방향)
        transform.translate(0, width);
        transform.rotate(3 * Math.PI / 2);
      }
      default -> {
        // 1 및 그 외 값은 호출 전에 걸러지므로 도달하지 않는다.
      }
    }
    return transform;
  }

  // open() 스트림에서 전체 바이트를 읽는다. 객체 부재(NoSuchKey)는 포트가 IMAGE_NOT_FOUND로 매핑한다.
  private byte[] readAllBytes(ImageObjectKey key) {
    try (InputStream inputStream = imageStoragePort.open(key)) {
      return inputStream.readAllBytes();
    } catch (IOException e) {
      // 스토리지 IO 실패는 일시적일 수 있으므로 디코딩 실패와 구분해 매핑한다(재인코딩 재시도 대상).
      throw new CustomException(ImageErrorCode.IMAGE_STORAGE_READ_FAILED);
    }
  }

  // 라스터 할당 전에 헤더 치수를 검증하고(공통 정책), 한도 내 이미지만 디코딩한다.
  private BufferedImage decodeWithinLimits(byte[] bytes) {
    imageObjectValidator.validateHeaderDimensions(new ByteArrayInputStream(bytes));
    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
      if (image == null) {
        throw new CustomException(ImageErrorCode.IMAGE_DECODE_FAILED);
      }
      return image;
    } catch (IOException e) {
      throw new CustomException(ImageErrorCode.IMAGE_DECODE_FAILED);
    }
  }

  private byte[] encodeJpeg(BufferedImage image) {
    BufferedImage rgb = toOpaqueRgb(image);
    try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
      if (!ImageIO.write(rgb, "jpg", os)) {
        throw new CustomException(ImageErrorCode.IMAGE_ENCODE_FAILED);
      }
      return os.toByteArray();
    } catch (IOException e) {
      throw new CustomException(ImageErrorCode.IMAGE_ENCODE_FAILED);
    }
  }

  // JPEG는 투명도를 못 담으므로 흰 배경에 합성해 불투명 RGB로 평탄화한다(PNG/WEBP/GIF 공통).
  private BufferedImage toOpaqueRgb(BufferedImage image) {
    BufferedImage rgb =
        new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D g = rgb.createGraphics();
    try {
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
      g.drawImage(image, 0, 0, null);
    } finally {
      g.dispose();
    }
    return rgb;
  }
}
