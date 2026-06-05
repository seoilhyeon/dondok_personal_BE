package com.oit.dondok.infra.image.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.domain.image.port.ImageObjectKey;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultImageObjectKeyPolicyTest {

  private static final UUID MEMBER_UUID = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
  private static final UUID FILE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  private final DefaultImageObjectKeyPolicy policy = new DefaultImageObjectKeyPolicy();

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

  @Test
  void matchesProfileKeyAcceptsOwnerScopedProfileKeyOnly() {
    UUID other = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c999");
    assertThat(policy.matchesProfileKey(MEMBER_UUID, "profile/" + MEMBER_UUID + "/" + FILE_ID))
        .isTrue();
    assertThat(policy.matchesProfileKey(MEMBER_UUID, "crew/" + MEMBER_UUID + "/" + FILE_ID))
        .isFalse();
    assertThat(policy.matchesProfileKey(MEMBER_UUID, "profile/" + other + "/" + FILE_ID)).isFalse();
    assertThat(policy.matchesProfileKey(MEMBER_UUID, "profile/" + MEMBER_UUID + "/not-a-uuid"))
        .isFalse();
    assertThat(policy.matchesProfileKey(MEMBER_UUID, null)).isFalse();
  }

  @Test
  void matchesCrewKeyAcceptsOwnerScopedCrewKeyOnly() {
    UUID other = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c999");
    assertThat(policy.matchesCrewKey(MEMBER_UUID, "crew/" + MEMBER_UUID + "/" + FILE_ID)).isTrue();
    assertThat(policy.matchesCrewKey(MEMBER_UUID, "profile/" + MEMBER_UUID + "/" + FILE_ID))
        .isFalse();
    assertThat(policy.matchesCrewKey(MEMBER_UUID, "mission/1/2/" + FILE_ID)).isFalse();
    assertThat(policy.matchesCrewKey(MEMBER_UUID, "crew/" + other + "/" + FILE_ID)).isFalse();
    assertThat(policy.matchesCrewKey(MEMBER_UUID, "crew/" + MEMBER_UUID + "/not-a-uuid")).isFalse();
    assertThat(policy.matchesCrewKey(MEMBER_UUID, null)).isFalse();
  }

  @Test
  void matchesMissionKeyAcceptsMatchingCrewAndParticipantOnly() {
    assertThat(policy.matchesMissionKey(42L, 101L, "mission/42/101/" + FILE_ID)).isTrue();
    assertThat(policy.matchesMissionKey(42L, 999L, "mission/42/101/" + FILE_ID)).isFalse();
    assertThat(policy.matchesMissionKey(42L, 101L, "profile/" + MEMBER_UUID + "/" + FILE_ID))
        .isFalse();
    assertThat(policy.matchesMissionKey(42L, 101L, "mission/42/101/not-a-uuid")).isFalse();
    assertThat(policy.matchesMissionKey(42L, 101L, null)).isFalse();
  }
}
