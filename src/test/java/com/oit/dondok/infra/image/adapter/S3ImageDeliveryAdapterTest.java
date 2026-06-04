package com.oit.dondok.infra.image.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.oit.dondok.domain.image.port.ImageDeliveryUrl;
import com.oit.dondok.domain.image.port.ImageObjectKey;
import java.net.URL;
import java.time.Duration;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3ImageDeliveryAdapterTest {

  @Mock private S3Presigner s3Presigner;

  @InjectMocks private S3ImageDeliveryAdapter adapter;

  @Test
  void createDeliveryUrlReturnsPresignedGetUrlAndSeoulExpiry() throws Exception {
    ReflectionTestUtils.setField(adapter, "bucket", "dondok-test-bucket");
    PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
    given(presigned.url()).willReturn(new URL("https://s3.example.com/get/profile/m/f"));
    given(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).willReturn(presigned);

    ImageDeliveryUrl result =
        adapter.createDeliveryUrl(new ImageObjectKey("profile/m/f"), Duration.ofMinutes(10));

    assertThat(result.url()).isEqualTo("https://s3.example.com/get/profile/m/f");
    // expiresAt은 Asia/Seoul offset(+09:00)으로 표현된다.
    assertThat(result.expiresAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
  }
}
