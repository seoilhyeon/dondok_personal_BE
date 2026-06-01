package com.oit.dondok.infra.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.infra.image.dto.PresignedUrlRequest;
import com.oit.dondok.infra.image.dto.PresignedUrlResponse;
import com.oit.dondok.infra.image.dto.UploadPurpose;
import com.oit.dondok.infra.image.exception.ImageErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

  private static final UUID MEMBER_UUID = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");

  @Mock private S3Presigner s3Presigner;

  @Mock private S3Client s3Client;

  @InjectMocks private ImageService imageService;

  @BeforeEach
  void setBucket() {
    ReflectionTestUtils.setField(imageService, "bucket", "dondok-test-bucket");
  }

  @Test
  void generatePresignedUrlRejectsMissionImageUntilOwnershipVerified() {
    // 소유권 검증이 구현되기 전까지 MISSION_IMAGE는 fail-closed로 차단한다 (IDOR 방지).
    assertThatThrownBy(
            () ->
                imageService.generatePresignedUrl(
                    MEMBER_UUID,
                    new PresignedUrlRequest(
                        UploadPurpose.MISSION_IMAGE, 42L, 101L, "image/jpeg", 2048L)))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.MISSION_IMAGE_UPLOAD_FORBIDDEN);

    // 차단된 요청은 presigned URL을 발급하지 않는다.
    verify(s3Presigner, never()).presignPutObject(any(PutObjectPresignRequest.class));
  }

  @Test
  void generatePresignedUrlBuildsProfileKeyForProfileImage() throws Exception {
    givenPresignedUrl("https://s3.example.com/upload");

    PresignedUrlResponse response =
        imageService.generatePresignedUrl(
            MEMBER_UUID,
            new PresignedUrlRequest(UploadPurpose.PROFILE_IMAGE, null, null, "image/jpeg", 2048L));

    assertThat(response.s3Key()).matches("profile/" + MEMBER_UUID + "/[0-9a-fA-F-]{36}");
  }

  @Test
  void generatePresignedUrlBuildsCrewKeyForCrewImage() throws Exception {
    givenPresignedUrl("https://s3.example.com/upload");

    PresignedUrlResponse response =
        imageService.generatePresignedUrl(
            MEMBER_UUID,
            new PresignedUrlRequest(UploadPurpose.CREW_IMAGE, null, null, "image/jpeg", 2048L));

    assertThat(response.s3Key()).matches("crew/" + MEMBER_UUID + "/[0-9a-fA-F-]{36}");
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

  private void givenPresignedUrl(String url) throws Exception {
    PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
    given(presigned.url()).willReturn(new URL(url));
    given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).willReturn(presigned);
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
