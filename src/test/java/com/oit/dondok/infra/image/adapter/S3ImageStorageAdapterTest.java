package com.oit.dondok.infra.image.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.image.port.ImageObjectMetadata;
import com.oit.dondok.domain.image.port.PresignedUpload;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import com.oit.dondok.infra.image.exception.ImageErrorCode;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
class S3ImageStorageAdapterTest {

  @Mock private S3Presigner s3Presigner;

  @Mock private S3Client s3Client;

  @InjectMocks private S3ImageStorageAdapter adapter;

  private void setBucket() {
    ReflectionTestUtils.setField(adapter, "bucket", "dondok-test-bucket");
  }

  @Test
  void createPresignedUploadReturnsUrlKeyAndSeoulExpiry() throws Exception {
    setBucket();
    PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
    given(presigned.url()).willReturn(new URL("https://s3.example.com/upload/profile/m/f"));
    given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).willReturn(presigned);

    ImageObjectKey key = new ImageObjectKey("profile/m/f");
    PresignedUpload upload =
        adapter.createPresignedUpload(key, "image/jpeg", Duration.ofMinutes(10));

    assertThat(upload.uploadUrl()).isEqualTo("https://s3.example.com/upload/profile/m/f");
    assertThat(upload.key()).isEqualTo(key);
    // expiresAt은 Asia/Seoul offset(+09:00)으로 표현된다.
    assertThat(upload.expiresAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
  }

  @Test
  void headMapsContentLengthAndContentType() {
    setBucket();
    given(s3Client.headObject(any(HeadObjectRequest.class)))
        .willReturn(
            HeadObjectResponse.builder().contentLength(2048L).contentType("image/jpeg").build());

    ImageObjectMetadata meta = adapter.head(new ImageObjectKey("profile/m/f"));

    assertThat(meta.contentLength()).isEqualTo(2048L);
    assertThat(meta.contentType()).isEqualTo("image/jpeg");
  }

  @Test
  void headThrowsImageNotFoundWhenObjectMissing() {
    setBucket();
    given(s3Client.headObject(any(HeadObjectRequest.class)))
        .willThrow(NoSuchKeyException.builder().build());

    assertThatThrownBy(() -> adapter.head(new ImageObjectKey("missing")))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.IMAGE_NOT_FOUND);
  }

  @Test
  void headMapsGeneric404ToImageNotFound() {
    // HeadObject는 본문이 없어 404가 NoSuchKeyException이 아닌 일반 S3Exception(404)으로 올 수 있다.
    setBucket();
    given(s3Client.headObject(any(HeadObjectRequest.class)))
        .willThrow(S3Exception.builder().statusCode(404).build());

    assertThatThrownBy(() -> adapter.head(new ImageObjectKey("missing")))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.IMAGE_NOT_FOUND);
  }

  @Test
  void headWrapsNon404S3ExceptionInServerError() {
    // 404가 아닌 S3 오류는 AWS 예외를 누출하지 않고 SERVER_ERROR로 감싼다 (원인 보존).
    setBucket();
    given(s3Client.headObject(any(HeadObjectRequest.class)))
        .willThrow(S3Exception.builder().statusCode(500).build());

    assertThatThrownBy(() -> adapter.head(new ImageObjectKey("boom")))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.SERVER_ERROR);
  }

  @Test
  void openReturnsObjectStream() throws Exception {
    setBucket();
    given(s3Client.getObject(any(GetObjectRequest.class)))
        .willReturn(s3Stream("bytes".getBytes(StandardCharsets.UTF_8)));

    try (InputStream stream = adapter.open(new ImageObjectKey("profile/m/f"))) {
      assertThat(stream.readAllBytes()).isEqualTo("bytes".getBytes(StandardCharsets.UTF_8));
    }
  }

  @Test
  void openThrowsImageNotFoundWhenObjectMissing() {
    setBucket();
    given(s3Client.getObject(any(GetObjectRequest.class)))
        .willThrow(NoSuchKeyException.builder().build());

    assertThatThrownBy(() -> adapter.open(new ImageObjectKey("missing")))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ImageErrorCode.IMAGE_NOT_FOUND);
  }

  @Test
  void putUploadsBytesViaS3Client() {
    setBucket();
    ArgumentCaptor<PutObjectRequest> requestCaptor =
        ArgumentCaptor.forClass(PutObjectRequest.class);

    adapter.put(new ImageObjectKey("profile/m/f"), new byte[] {1, 2, 3}, "image/jpeg");

    // bucket/key/contentType이 정확히 전달되는지 검증한다.
    verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
    PutObjectRequest captured = requestCaptor.getValue();
    assertThat(captured.bucket()).isEqualTo("dondok-test-bucket");
    assertThat(captured.key()).isEqualTo("profile/m/f");
    assertThat(captured.contentType()).isEqualTo("image/jpeg");
  }

  private static ResponseInputStream<GetObjectResponse> s3Stream(byte[] bytes) {
    return new ResponseInputStream<>(
        GetObjectResponse.builder().build(),
        AbortableInputStream.create(new ByteArrayInputStream(bytes)));
  }
}
