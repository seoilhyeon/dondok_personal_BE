package com.oit.dondok.infra.image.service;

import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.image.port.ImageObjectKeyPolicy;
import com.oit.dondok.domain.image.port.ImageStoragePort;
import com.oit.dondok.domain.image.port.PresignedUpload;
import com.oit.dondok.domain.mission.service.MissionImageService;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.infra.image.dto.PresignedUrlRequest;
import com.oit.dondok.infra.image.dto.PresignedUrlResponse;
import com.oit.dondok.infra.image.dto.UploadPurpose;
import com.oit.dondok.infra.image.exception.ImageErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImageService {

  private static final Duration PRESIGN_DURATION = Duration.ofMinutes(10);

  private final ImageStoragePort imageStoragePort;
  private final ImageObjectKeyPolicy keyPolicy;
  private final ImageObjectValidator imageObjectValidator;
  private final MissionImageService missionImageService;

  public PresignedUrlResponse generatePresignedUrl(UUID memberUuid, PresignedUrlRequest request) {
    // presigned URL 발급은 특정 S3 namespace에 대한 업로드 권한 위임이므로, 발급 전에 요청자가
    // 해당 namespace에 업로드할 자격이 있는지, 그리고 파일이 정책(형식/크기)을 만족하는지 검증한다.
    verifyUploadPermission(memberUuid, request);
    imageObjectValidator.validateContentPolicy(request.contentType(), request.contentLength());

    // key는 클라이언트가 지정하지 못하도록 서버가 정책으로 생성하고, 서명된 업로드 URL 발급은 포트에 위임한다.
    ImageObjectKey key = buildObjectKey(memberUuid, request);
    PresignedUpload upload =
        imageStoragePort.createPresignedUpload(key, request.contentType(), PRESIGN_DURATION);

    // 응답 계약 유지: upload_url / s3_key(=key.value()) / expires_at
    return PresignedUrlResponse.of(upload.uploadUrl(), upload.key().value(), upload.expiresAt());
  }

  // purpose별 key 생성을 ImageObjectKeyPolicy에 위임한다 (key 형식의 단일 출처).
  // - MISSION_IMAGE: mission/{crewId}/{crewParticipantId}/{uuid}
  // - PROFILE_IMAGE: profile/{memberUuid}/{uuid}
  // - CREW_IMAGE:    crew/{memberUuid}/{uuid}
  private ImageObjectKey buildObjectKey(UUID memberUuid, PresignedUrlRequest request) {
    UUID fileId = UUID.randomUUID();
    return switch (request.purpose()) {
      case MISSION_IMAGE ->
          keyPolicy.missionImageKey(request.crewId(), request.crewParticipantId(), fileId);
      case PROFILE_IMAGE -> keyPolicy.profileImageKey(memberUuid, fileId);
      case CREW_IMAGE -> keyPolicy.crewImageKey(memberUuid, fileId);
    };
  }

  // 업로드 namespace에 대한 권한을 purpose별로 검증한다 (검증할 게 없는 purpose는 통과시킨다).
  // - PROFILE_IMAGE / CREW_IMAGE: key가 인증된 본인 memberUuid namespace로 서버에서 생성되므로
  //   타인 namespace를 가리킬 수 없어 별도 권한 검증이 불필요하다.
  //   (CREW_IMAGE에 host 전용 같은 권한 규칙이 필요해지면 그때 이 분기에 추가한다.)
  // - MISSION_IMAGE: crewId/participantId가 클라이언트 요청 값이라 타인 participant namespace
  //   접근(IDOR)이 가능하므로, 참여자 소유권 검증을 mission 도메인(MissionImageService)에 위임한다.
  //   위반 시 PARTICIPANT_NOT_FOUND로 차단된다.
  private void verifyUploadPermission(UUID memberUuid, PresignedUrlRequest request) {
    if (request.purpose() == UploadPurpose.MISSION_IMAGE) {
      missionImageService.getOwnedParticipant(
          memberUuid, request.crewId(), request.crewParticipantId());
    }
  }

  public void reEncodeImage(String objectKey) {
    ImageObjectKey key = new ImageObjectKey(objectKey);
    // 다운로드/디코딩 전에 존재/크기/타입을 공통 정책(ImageObjectValidator)으로 선검증한다.
    imageObjectValidator.validate(key);

    BufferedImage image = readImage(key);
    byte[] reEncoded = encodeJpeg(image);

    // 재인코딩 결과도 동일 정책으로 재검증한다 (거대 픽셀 원본이 한도 초과 JPEG로 팽창하는 경우 차단).
    imageObjectValidator.validateContentPolicy("image/jpeg", reEncoded.length);
    // 정제본을 같은 key로 덮어쓴다. 객체 부재(NoSuchKey)는 포트가 IMAGE_NOT_FOUND로 매핑한다.
    imageStoragePort.put(key, reEncoded, "image/jpeg");
  }

  // 저장소에서 원본을 내려받아 디코드한다. 읽기/디코드 실패는 IMAGE_READ_FAILED로 매핑한다.
  private BufferedImage readImage(ImageObjectKey key) {
    try (InputStream inputStream = imageStoragePort.open(key)) {
      BufferedImage image = ImageIO.read(inputStream);
      if (image == null) {
        // 객체는 읽혔으나 이미지로 디코드되지 않는 경우(손상/비-이미지). 부재(NoSuchKey)는 open()이 처리한다.
        throw new CustomException(ImageErrorCode.IMAGE_READ_FAILED);
      }
      return image;
    } catch (IOException e) {
      throw new CustomException(ImageErrorCode.IMAGE_READ_FAILED);
    }
  }

  // JPG로 재인코딩한다 (EXIF 메타데이터 자동 제거). 쓰기 실패는 IMAGE_ENCODE_FAILED로 매핑한다.
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
