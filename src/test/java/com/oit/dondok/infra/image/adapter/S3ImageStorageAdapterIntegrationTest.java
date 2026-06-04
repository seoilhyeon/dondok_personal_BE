package com.oit.dondok.infra.image.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.IntegrationTest;
import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.image.port.ImageObjectMetadata;
import com.oit.dondok.domain.image.port.ImageStoragePort;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

// test가 아닌 프로파일에서 ImageStoragePort가 실제 S3 구현(S3ImageStorageAdapter)으로 바인딩되는지,
// 그리고 awspring S3 빈 wiring + 프로퍼티 바인딩이 실제 동작하는지를 LocalStack으로 검증하는 스모크 테스트.
// @IntegrationTest는 MySQL/Redis Testcontainers + @SpringBootTest를 제공하고, 프로파일만 integration-s3로 확장한다.
@IntegrationTest
@ActiveProfiles({"integration", "integration-s3"})
@Testcontainers
class S3ImageStorageAdapterIntegrationTest {

  private static final String BUCKET = "dondok-it-bucket";

  @Container
  static LocalStackContainer localStack =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.4"))
          .withServices(Service.S3);

  @DynamicPropertySource
  static void awsProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.cloud.aws.s3.endpoint", () -> localStack.getEndpoint().toString());
    registry.add("spring.cloud.aws.region.static", localStack::getRegion);
    registry.add("spring.cloud.aws.credentials.access-key", localStack::getAccessKey);
    registry.add("spring.cloud.aws.credentials.secret-key", localStack::getSecretKey);
  }

  @Autowired private ImageStoragePort imageStoragePort;

  @Autowired private S3Client s3Client;

  @BeforeEach
  void createBucket() {
    try {
      s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    } catch (BucketAlreadyOwnedByYouException ignored) {
      // 동일 컨테이너를 재사용하는 경우 버킷이 이미 존재할 수 있다.
    }
  }

  @Test
  void bindsToS3AdapterAndRoundTripsObjectViaLocalStack() {
    // @Profile("!test") 분기로 실제 S3 구현이 주입되었는지 먼저 확인한다.
    assertThat(imageStoragePort).isInstanceOf(S3ImageStorageAdapter.class);

    ImageObjectKey key = new ImageObjectKey("profile/it/round-trip.jpg");
    byte[] payload = "dondok-integration".getBytes(StandardCharsets.UTF_8);

    imageStoragePort.put(key, payload, "image/jpeg");

    // put → head 대표 경로: 메타데이터가 LocalStack에서 그대로 회수되는지 검증한다.
    ImageObjectMetadata metadata = imageStoragePort.head(key);
    assertThat(metadata.contentLength()).isEqualTo(payload.length);
    assertThat(metadata.contentType()).isEqualTo("image/jpeg");
  }
}
