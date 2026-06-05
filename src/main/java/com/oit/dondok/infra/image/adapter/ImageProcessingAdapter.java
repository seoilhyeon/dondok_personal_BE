package com.oit.dondok.infra.image.adapter;

import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.image.port.ImageStoragePort;
import com.oit.dondok.domain.mission.port.ImageProcessingPort;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.infra.image.exception.ImageErrorCode;
import com.oit.dondok.infra.image.service.ImageObjectValidator;
import java.awt.image.BufferedImage;
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

    BufferedImage image = readImage(key);
    byte[] reEncoded = encodeJpeg(image);

    // 재인코딩 결과도 동일 정책으로 재검증(거대 픽셀 원본이 한도 초과 JPEG로 팽창하는 경우 차단)
    imageObjectValidator.validateContentPolicy("image/jpeg", reEncoded.length);
    imageStoragePort.put(key, reEncoded, "image/jpeg");
  }

  private BufferedImage readImage(ImageObjectKey key) {
    try (InputStream inputStream = imageStoragePort.open(key)) {
      BufferedImage image = ImageIO.read(inputStream);
      if (image == null) {
        throw new CustomException(ImageErrorCode.IMAGE_READ_FAILED);
      }
      return image;
    } catch (IOException e) {
      throw new CustomException(ImageErrorCode.IMAGE_READ_FAILED);
    }
  }

  private byte[] encodeJpeg(BufferedImage image) {
    try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
      if (!ImageIO.write(image, "jpg", os)) {
        throw new CustomException(ImageErrorCode.IMAGE_ENCODE_FAILED);
      }
      return os.toByteArray();
    } catch (IOException e) {
      throw new CustomException(ImageErrorCode.IMAGE_ENCODE_FAILED);
    }
  }
}
