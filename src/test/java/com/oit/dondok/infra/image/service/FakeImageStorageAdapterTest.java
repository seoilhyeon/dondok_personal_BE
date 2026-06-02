package com.oit.dondok.infra.image.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.image.port.PresignedUpload;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class FakeImageStorageAdapterTest {

  @Test
  void createPresignedUploadReturnsDeterministicNonProdUrlFromObjectKey() {
    FakeImageStorageAdapter adapter = new FakeImageStorageAdapter();

    PresignedUpload upload =
        adapter.createPresignedUpload(
            new ImageObjectKey("profile/018f4fd2/avatar.jpg"),
            "image/jpeg",
            Duration.ofMinutes(10));

    assertThat(upload.uploadUrl())
        .isEqualTo("https://s3.example.com/upload/profile/018f4fd2/avatar.jpg");
    assertThat(upload.key()).isEqualTo(new ImageObjectKey("profile/018f4fd2/avatar.jpg"));
  }
}
