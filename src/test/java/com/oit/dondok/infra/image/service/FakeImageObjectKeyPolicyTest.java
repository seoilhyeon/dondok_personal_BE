package com.oit.dondok.infra.image.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.domain.image.port.ImageObjectKey;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FakeImageObjectKeyPolicyTest {

  private static final UUID MEMBER_UUID = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
  private static final UUID FILE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  private final FakeImageObjectKeyPolicy policy = new FakeImageObjectKeyPolicy();

  @Test
  void missionImageKeyUsesCrewAndParticipantNamespace() {
    ImageObjectKey key = policy.missionImageKey(42L, 101L, FILE_ID);

    assertThat(key.value()).isEqualTo("mission/42/101/11111111-1111-1111-1111-111111111111");
  }

  @Test
  void profileImageKeyUsesMemberNamespace() {
    ImageObjectKey key = policy.profileImageKey(MEMBER_UUID, FILE_ID);

    assertThat(key.value())
        .isEqualTo(
            "profile/018f4fd2-6d7a-7a41-9f58-6d07f5c3c901/11111111-1111-1111-1111-111111111111");
  }

  @Test
  void crewImageKeyUsesMemberNamespace() {
    ImageObjectKey key = policy.crewImageKey(MEMBER_UUID, FILE_ID);

    assertThat(key.value())
        .isEqualTo(
            "crew/018f4fd2-6d7a-7a41-9f58-6d07f5c3c901/11111111-1111-1111-1111-111111111111");
  }
}
