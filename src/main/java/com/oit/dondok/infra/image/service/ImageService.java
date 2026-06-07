package com.oit.dondok.infra.image.service;

import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.image.port.ImageObjectKeyPolicy;
import com.oit.dondok.domain.image.port.ImageStoragePort;
import com.oit.dondok.domain.image.port.PresignedUpload;
import com.oit.dondok.domain.mission.service.MissionImageService;
import com.oit.dondok.infra.image.dto.PresignedUrlRequest;
import com.oit.dondok.infra.image.dto.PresignedUrlResponse;
import com.oit.dondok.infra.image.dto.UploadPurpose;
import java.time.Duration;
import java.util.UUID;
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
}
