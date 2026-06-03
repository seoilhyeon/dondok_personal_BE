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
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImageService {

  // FE가 모든 이미지를 JPG로 변환해 업로드하므로 BE는 image/jpeg만 허용한다.
  // (BE에서 다른 포맷도 직접 받으려면 이 집합만 확장하면 된다.)
  private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg");
  private static final long MAX_CONTENT_LENGTH = 10L * 1024 * 1024; // 10MB
  private static final Duration PRESIGN_DURATION = Duration.ofMinutes(10);

  private final ImageStoragePort imageStoragePort;
  private final ImageObjectKeyPolicy keyPolicy;
  private final ImageObjectValidator imageObjectValidator;
  private final MissionImageService missionImageService;

  public PresignedUrlResponse generatePresignedUrl(UUID memberUuid, PresignedUrlRequest request) {
    // presigned URL 발급은 특정 S3 namespace에 대한 업로드 권한 위임이므로, 발급 전에 요청자가
    // 해당 namespace에 업로드할 자격이 있는지, 그리고 파일이 정책(형식/크기)을 만족하는지 검증한다.
    verifyUploadPermission(memberUuid, request);
    validateContentPolicy(request);

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

  // presign 발급 시점, 클라이언트가 신고한 형식/크기를 검증한다 (object가 아직 없으므로 request 기준)
  private void validateContentPolicy(PresignedUrlRequest request) {
    if (!ALLOWED_CONTENT_TYPES.contains(request.contentType())) {
      throw new CustomException(ImageErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }
    if (request.contentLength() > MAX_CONTENT_LENGTH) {
      throw new CustomException(ImageErrorCode.IMAGE_TOO_LARGE);
    }
  }

  // 업로드 namespace에 대한 권한을 검증한다.
  // TODO: PROFILE_IMAGE, CREW_IMAGE case 추가
  // - PROFILE_IMAGE / CREW_IMAGE: key가 요청자 본인 memberUuid namespace이므로 소유권이 내재적으로 보장된다.
  // - MISSION_IMAGE: 타인 participant namespace 접근(IDOR)을 막기 위해, 참여자 소유권 검증(decision)을
  //   mission 도메인(MissionImageService)에 위임한다. 위반 시 PARTICIPANT_NOT_FOUND로 차단된다.
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

    // try-with-resources로 스토리지 InputStream과 출력 스트림을 닫는다.
    try (InputStream inputStream = imageStoragePort.open(key);
        ByteArrayOutputStream os = new ByteArrayOutputStream()) {
      // InputStream을 BufferedImage로 변환
      BufferedImage image = ImageIO.read(inputStream);
      if (image == null) {
        throw new CustomException(ImageErrorCode.IMAGE_READ_FAILED);
      }

      // JPG로 재인코딩 (Exif 메타데이터 자동 제거)
      if (!ImageIO.write(image, "jpg", os)) {
        throw new CustomException(ImageErrorCode.IMAGE_ENCODE_FAILED);
      }

      // 정제본을 같은 key로 덮어쓴다. 객체 부재(NoSuchKey)는 포트가 IMAGE_NOT_FOUND로 매핑한다.
      imageStoragePort.put(key, os.toByteArray(), "image/jpeg");
    } catch (IOException e) {
      throw new CustomException(ImageErrorCode.IMAGE_ENCODE_FAILED);
    }
  }
}
