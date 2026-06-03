package com.oit.dondok.infra.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.oit.dondok.domain.crew.exception.CrewErrorCode;
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

  private static final UUID MEMBER_UUID = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
  // 발급 만료 시각은 포트(어댑터)가 결정하므로, 서비스 테스트에서는 고정 값을 stub해 그대로 전달되는지만 본다.
  private static final OffsetDateTime EXPIRES_AT =
      OffsetDateTime.parse("2026-06-03T12:00:00+09:00");

  @Mock private ImageStoragePort imageStoragePort;

  @Mock private ImageObjectKeyPolicy keyPolicy;

  @Mock private S3Client s3Client;

  @Mock private MissionImageService missionImageService;

  @InjectMocks private ImageService imageService;

  @BeforeEach
  void setBucket() {
    // reEncodeImage가 사용하는 버킷 값 주입 (presign 경로는 포트 위임이라 버킷을 직접 쓰지 않는다).
    ReflectionTestUtils.setField(imageService, "bucket", "dondok-test-bucket");
  }

  @Test
  void generatePresignedUrlDelegatesMissionKeyToPolicyWhenOwnershipValid() {
    // MISSION_IMAGE는 mission 도메인에 소유권 검증을 위임한 뒤, key 생성은 정책에 / 발급은 포트에 위임한다.
    ImageObjectKey key = new ImageObjectKey("mission/42/101/file");
    givenIssuedUpload(key);

    PresignedUrlResponse response =
        imageService.generatePresignedUrl(
            MEMBER_UUID,
            new PresignedUrlRequest(UploadPurpose.MISSION_IMAGE, 42L, 101L, "image/jpeg", 2048L));

    // 인증 사용자/crew/participant 기준으로 소유권 검증이 위임되는지 확인한다.
    verify(missionImageService).getOwnedParticipant(eq(MEMBER_UUID), eq(42L), eq(101L));
    verify(keyPolicy).missionImageKey(eq(42L), eq(101L), any(UUID.class));
    assertResponseMatches(response, key);
  }

  @Test
  void generatePresignedUrlPropagatesWhenMissionParticipantNotOwned() {
    // 소유권 검증 실패 시 key 생성/발급으로 진행하지 않고 예외를 전파한다 (IDOR 방지).
    given(missionImageService.getOwnedParticipant(any(), any(), any()))
        .willThrow(new CustomException(CrewErrorCode.PARTICIPANT_NOT_FOUND));

    assertThatThrownBy(
            () ->
                imageService.generatePresignedUrl(
                    MEMBER_UUID,
                    new PresignedUrlRequest(
                        UploadPurpose.MISSION_IMAGE, 42L, 101L, "image/jpeg", 2048L)))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.PARTICIPANT_NOT_FOUND);

    verify(imageStoragePort, never()).createPresignedUpload(any(), any(), any());
  }

  @Test
  void generatePresignedUrlDelegatesProfileKeyToPolicy() {
    ImageObjectKey key = new ImageObjectKey("profile/" + MEMBER_UUID + "/file");
    given(keyPolicy.profileImageKey(eq(MEMBER_UUID), any(UUID.class))).willReturn(key);
    given(imageStoragePort.createPresignedUpload(eq(key), eq("image/jpeg"), any(Duration.class)))
        .willReturn(uploadFor(key));

    PresignedUrlResponse response =
        imageService.generatePresignedUrl(
            MEMBER_UUID,
            new PresignedUrlRequest(UploadPurpose.PROFILE_IMAGE, null, null, "image/jpeg", 2048L));

    assertResponseMatches(response, key);
  }

  @Test
  void generatePresignedUrlDelegatesCrewKeyToPolicy() {
    ImageObjectKey key = new ImageObjectKey("crew/" + MEMBER_UUID + "/file");
    given(keyPolicy.crewImageKey(eq(MEMBER_UUID), any(UUID.class))).willReturn(key);
    given(imageStoragePort.createPresignedUpload(eq(key), eq("image/jpeg"), any(Duration.class)))
        .willReturn(uploadFor(key));

    PresignedUrlResponse response =
        imageService.generatePresignedUrl(
            MEMBER_UUID,
            new PresignedUrlRequest(UploadPurpose.CREW_IMAGE, null, null, "image/jpeg", 2048L));

    assertResponseMatches(response, key);
  }

  @Test
  void generatePresignedUrlThrowsWhenContentTypeNotAllowed() {
    assertThatThrownBy(
            () ->
                imageService.generatePresignedUrl(
                    MEMBER_UUID,
                    new PresignedUrlRequest(
                        UploadPurpose.PROFILE_IMAGE, null, null, "image/gif", 2048L)))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.UNSUPPORTED_IMAGE_TYPE);

    // 정책 위반은 key 생성/발급 전에 차단된다.
    verify(imageStoragePort, never()).createPresignedUpload(any(), any(), any());
  }

  @Test
  void generatePresignedUrlThrowsWhenContentLengthExceedsMax() {
    long tooLarge = 10L * 1024 * 1024 + 1;

    assertThatThrownBy(
            () ->
                imageService.generatePresignedUrl(
                    MEMBER_UUID,
                    new PresignedUrlRequest(
                        UploadPurpose.PROFILE_IMAGE, null, null, "image/jpeg", tooLarge)))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.IMAGE_TOO_LARGE);

    verify(imageStoragePort, never()).createPresignedUpload(any(), any(), any());
  }

  @Test
  void reEncodeImageReuploadsReEncodedImage() throws Exception {
    given(s3Client.headObject(any(HeadObjectRequest.class)))
        .willReturn(headResponse(2048L, "image/jpeg"));
    given(s3Client.getObject(any(GetObjectRequest.class))).willReturn(s3Stream(sampleImageBytes()));

    imageService.reEncodeImage("mission/42/101/abc");

    // 재인코딩된 이미지가 같은 key로 다시 업로드된다.
    verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void reEncodeImageThrowsImageNotFoundWhenObjectMissing() {
    // HeadObject 단계에서 객체 부재가 먼저 감지된다.
    given(s3Client.headObject(any(HeadObjectRequest.class)))
        .willThrow(NoSuchKeyException.builder().build());

    assertThatThrownBy(() -> imageService.reEncodeImage("mission/42/101/missing"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.IMAGE_NOT_FOUND);

    verify(s3Client, never()).getObject(any(GetObjectRequest.class));
  }

  @Test
  void reEncodeImageThrowsImageReadFailedWhenObjectIsNotImage() {
    given(s3Client.headObject(any(HeadObjectRequest.class)))
        .willReturn(headResponse(2048L, "image/jpeg"));
    given(s3Client.getObject(any(GetObjectRequest.class)))
        .willReturn(s3Stream("not-an-image".getBytes(StandardCharsets.UTF_8)));

    assertThatThrownBy(() -> imageService.reEncodeImage("mission/42/101/broken"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.IMAGE_READ_FAILED);
  }

  @Test
  void reEncodeImageRejectsTooLargeBeforeDownload() {
    given(s3Client.headObject(any(HeadObjectRequest.class)))
        .willReturn(headResponse(10L * 1024 * 1024 + 1, "image/jpeg"));

    assertThatThrownBy(() -> imageService.reEncodeImage("mission/42/101/huge"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.IMAGE_TOO_LARGE);

    // 크기 초과는 다운로드/디코딩 전에 차단되어야 한다.
    verify(s3Client, never()).getObject(any(GetObjectRequest.class));
  }

  @Test
  void reEncodeImageRejectsUnsupportedTypeBeforeDownload() {
    given(s3Client.headObject(any(HeadObjectRequest.class)))
        .willReturn(headResponse(2048L, "image/gif"));

    assertThatThrownBy(() -> imageService.reEncodeImage("mission/42/101/gif"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.UNSUPPORTED_IMAGE_TYPE);

    verify(s3Client, never()).getObject(any(GetObjectRequest.class));
  }

  // MISSION_IMAGE 경로용 stub: random fileId는 예측 불가하므로 any(UUID)로 매칭한다.
  private void givenIssuedUpload(ImageObjectKey key) {
    given(keyPolicy.missionImageKey(any(Long.class), any(Long.class), any(UUID.class)))
        .willReturn(key);
    given(imageStoragePort.createPresignedUpload(eq(key), eq("image/jpeg"), any(Duration.class)))
        .willReturn(uploadFor(key));
  }

  private static PresignedUpload uploadFor(ImageObjectKey key) {
    return new PresignedUpload("https://s3.example.com/upload/" + key.value(), key, EXPIRES_AT);
  }

  // 응답이 발급 결과(PresignedUpload)를 계약대로(upload_url / s3_key / expires_at) 매핑하는지 확인한다.
  private static void assertResponseMatches(PresignedUrlResponse response, ImageObjectKey key) {
    assertThat(response.uploadUrl()).isEqualTo("https://s3.example.com/upload/" + key.value());
    assertThat(response.s3Key()).isEqualTo(key.value());
    assertThat(response.expiresAt()).isEqualTo(EXPIRES_AT);
  }

  private static HeadObjectResponse headResponse(long contentLength, String contentType) {
    return HeadObjectResponse.builder()
        .contentLength(contentLength)
        .contentType(contentType)
        .build();
  }

  private static ResponseInputStream<GetObjectResponse> s3Stream(byte[] bytes) {
    return new ResponseInputStream<>(
        GetObjectResponse.builder().build(),
        AbortableInputStream.create(new ByteArrayInputStream(bytes)));
  }

  private static byte[] sampleImageBytes() throws IOException {
    BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    ImageIO.write(image, "png", os);
    return os.toByteArray();
  }
}
