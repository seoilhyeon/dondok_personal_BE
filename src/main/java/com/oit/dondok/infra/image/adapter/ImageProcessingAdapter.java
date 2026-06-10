package com.oit.dondok.infra.image.adapter;

import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.image.port.ImageStoragePort;
import com.oit.dondok.domain.mission.port.ImageProcessingPort;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.infra.image.exception.ImageErrorCode;
import com.oit.dondok.infra.image.service.ImageObjectValidator;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// ImageProcessingPort 구현: 원본을 디코딩 -> JPG 재인코딩(EXIF 제거) -> 같은 key에 덮어쓴다.
@Component
@RequiredArgsConstructor
public class ImageProcessingAdapter implements ImageProcessingPort {

  private final ImageStoragePort imageStoragePort;
  private final ImageObjectValidator imageObjectValidator;

  @Override
  public void reEncode(String s3Key) {
    ImageObjectKey key = new ImageObjectKey(s3Key);
    // 다운로드/디코딩 전 존재/크기/타입 선검증 (없으면 어댑터가 IMAGE_NOT_FOUND 매핑)
    imageObjectValidator.validate(key);

    byte[] original = readAllBytes(key);
    BufferedImage image = decodeWithinLimits(original);
    byte[] reEncoded = encodeJpeg(image);

    // 재인코딩 결과도 동일 정책으로 재검증(거대 픽셀 원본이 한도 초과 JPEG로 팽창하는 경우 차단)
    imageObjectValidator.validateContentPolicy("image/jpeg", reEncoded.length);
    imageStoragePort.put(key, reEncoded, "image/jpeg");
  }

  // open() 스트림에서 전체 바이트를 읽는다. 객체 부재(NoSuchKey)는 포트가 IMAGE_NOT_FOUND로 매핑한다.
  private byte[] readAllBytes(ImageObjectKey key) {
    try (InputStream inputStream = imageStoragePort.open(key)) {
      return inputStream.readAllBytes();
    } catch (IOException e) {
      throw new CustomException(ImageErrorCode.IMAGE_READ_FAILED);
    }
  }

  // 라스터 할당 전에 헤더 치수를 검증하고(공통 정책), 한도 내 이미지만 디코딩한다.
  private BufferedImage decodeWithinLimits(byte[] bytes) {
    imageObjectValidator.validateDimensions(new ByteArrayInputStream(bytes));
    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
      if (image == null) {
        throw new CustomException(ImageErrorCode.IMAGE_READ_FAILED);
      }
      return image;
    } catch (IOException e) {
      throw new CustomException(ImageErrorCode.IMAGE_READ_FAILED);
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
