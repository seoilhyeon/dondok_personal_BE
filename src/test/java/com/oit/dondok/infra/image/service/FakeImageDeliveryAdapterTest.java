package com.oit.dondok.infra.image.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.domain.image.port.ImageDeliveryUrl;
import com.oit.dondok.domain.image.port.ImageObjectKey;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class FakeImageDeliveryAdapterTest {

  @Test
  void createDeliveryUrlReturnsDeterministicNonProdUrlFromObjectKey() {
    FakeImageDeliveryAdapter adapter = new FakeImageDeliveryAdapter();

    ImageDeliveryUrl deliveryUrl =
        adapter.createDeliveryUrl(
            new ImageObjectKey("profile/018f4fd2/avatar.jpg"), Duration.ofMinutes(10));

    assertThat(deliveryUrl.url()).isEqualTo("https://cdn.example.com/profile/018f4fd2/avatar.jpg");
  }
}
