package com.oit.dondok.infra.image.service;

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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class ImageService {

  // FE가 모든 이미지를 JPG로 변환해 업로드하므로 BE는 image/jpeg만 허용한다.
  // (BE에서 다른 포맷도 직접 받으려면 이 집합만 확장하면 된다.)
  private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg");
  private static final long MAX_CONTENT_LENGTH = 10L * 1024 * 1024; // 10MB
  private static final Duration PRESIGN_DURATION = Duration.ofMinutes(10);
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final S3Presigner s3Presigner;
  private final S3Client s3Client;
  private final MissionImageService missionImageService;

  @Value("${app.aws.s3.bucket}")
  private String bucket;

  public PresignedUrlResponse generatePresignedUrl(UUID memberUuid, PresignedUrlRequest request) {
    // presigned URL 발급은 특정 S3 namespace에 대한 업로드 권한 위임이므로, 발급 전에 요청자가
    // 해당 namespace에 업로드할 자격이 있는지, 그리고 파일이 정책(형식/크기)을 만족하는지 검증한다.
    verifyUploadPermission(memberUuid, request);
    validateContentPolicy(request);

    // S3 object key는 클라이언트가 지정하지 못하도록 서버가 purpose별로 생성한다.
    String objectKey = buildObjectKey(memberUuid, request);

    // S3에 PUT 요청을 허용하는 서명된 URL 생성 요청
    PutObjectPresignRequest presignRequest =
        PutObjectPresignRequest.builder()
            .signatureDuration(PRESIGN_DURATION) // 만료 후 업로드 불가
            .putObjectRequest(
                r -> r.bucket(bucket).key(objectKey).contentType(request.contentType()))
            .build();

    // S3가 서명된 URL 반환
    String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();
    OffsetDateTime expiresAt = OffsetDateTime.now(SEOUL).plus(PRESIGN_DURATION);

    return PresignedUrlResponse.of(uploadUrl, objectKey, expiresAt);
  }

  // purpose별 S3 key prefix를 적용한다.
  // - MISSION_IMAGE: mission/{crewId}/{crewParticipantId}/{uuid}
  // - PROFILE_IMAGE: profile/{memberUuid}/{uuid}
  // - CREW_IMAGE: crew/{memberUuid}/{uuid}
  private String buildObjectKey(UUID memberUuid, PresignedUrlRequest request) {
    UUID fileId = UUID.randomUUID();
    return switch (request.purpose()) {
      case MISSION_IMAGE ->
          String.format("mission/%d/%d/%s", request.crewId(), request.crewParticipantId(), fileId);
      case PROFILE_IMAGE -> String.format("profile/%s/%s", memberUuid, fileId);
      case CREW_IMAGE -> String.format("crew/%s/%s", memberUuid, fileId);
    };
  }

  // 허용 MIME type, 최대 파일 크기 등 발급 시점에 판단 가능한 정책을 검증한다.
  private void validateContentPolicy(PresignedUrlRequest request) {
    if (!ALLOWED_CONTENT_TYPES.contains(request.contentType())) {
      throw new CustomException(ImageErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }
    if (request.contentLength() > MAX_CONTENT_LENGTH) {
      throw new CustomException(ImageErrorCode.IMAGE_TOO_LARGE);
    }
  }

  // 업로드 namespace에 대한 권한을 검증한다.
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
    // 다운로드/디코딩 전에 HeadObject로 크기·타입을 먼저 검사해, 큰 object로 인한 메모리 압박을 막는다.
    verifyObjectWithinPolicy(objectKey);

    // try-with-resources로 S3 InputStream과 출력 스트림을 닫는다.
    try (InputStream inputStream = downloadImage(objectKey);
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

      // 정제본을 같은 objectKey로 S3에 덮어쓰기
      s3Client.putObject(
          PutObjectRequest.builder()
              .bucket(bucket)
              .key(objectKey)
              .contentType("image/jpeg")
              .build(),
          RequestBody.fromBytes(os.toByteArray()));
    } catch (NoSuchKeyException e) {
      // S3에 원본 객체가 없는 경우(404/NoSuchKey)
      throw new CustomException(ImageErrorCode.IMAGE_NOT_FOUND);
    } catch (IOException e) {
      throw new CustomException(ImageErrorCode.IMAGE_ENCODE_FAILED);
    }
  }

  // HeadObject로 메타데이터만 조회해 크기/타입을 선검증한다. 본문은 내려받지 않는다.
  private void verifyObjectWithinPolicy(String objectKey) {
    HeadObjectResponse head;
    try {
      head = s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(objectKey).build());
    } catch (NoSuchKeyException e) {
      throw new CustomException(ImageErrorCode.IMAGE_NOT_FOUND);
    }

    if (head.contentLength() != null && head.contentLength() > MAX_CONTENT_LENGTH) {
      throw new CustomException(ImageErrorCode.IMAGE_TOO_LARGE);
    }
    if (!ALLOWED_CONTENT_TYPES.contains(head.contentType())) {
      throw new CustomException(ImageErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }
  }

  // S3에서 원본 이미지 스트림 다운로드
  private InputStream downloadImage(String objectKey) {
    return s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(objectKey).build());
  }
}
