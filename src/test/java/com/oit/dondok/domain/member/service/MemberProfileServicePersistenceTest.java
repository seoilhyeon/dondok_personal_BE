package com.oit.dondok.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.config.JpaAuditingConfig;
import com.oit.dondok.config.QuerydslConfig;
import com.oit.dondok.domain.member.dto.request.UpdateProfileRequest;
import com.oit.dondok.domain.member.dto.response.ProfileResponse;
import com.oit.dondok.domain.member.dto.response.ProfileUpdateResponse;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.entity.MemberStatus;
import com.oit.dondok.domain.member.port.ProfileImageUrlResolver;
import com.oit.dondok.domain.member.repository.MemberProfileQueryRepository;
import com.oit.dondok.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
@Import({
  JpaAuditingConfig.class,
  QuerydslConfig.class,
  MemberProfileQueryRepository.class,
  MemberProfileService.class,
  MemberProfileServicePersistenceTest.TestProfileImageUrlResolverConfig.class
})
class MemberProfileServicePersistenceTest {

  @Autowired private MemberRepository memberRepository;
  @Autowired private MemberProfileService memberProfileService;

  @Test
  void findProfileByMemberUuidReflectsProfileUpdate() {
    Member member =
        memberRepository.saveAndFlush(Member.create("member@example.com", "hash", "기존닉네임"));
    UpdateProfileRequest request =
        new UpdateProfileRequest(
            JsonNullable.of("새닉네임"),
            JsonNullable.of("profile-images/new-image.png"),
            JsonNullable.of("새 상태 메시지"));

    ProfileUpdateResponse updateResponse =
        memberProfileService.updateProfile(member.getUuid(), request);
    ProfileResponse profileResponse =
        memberProfileService.findProfileByMemberUuid(member.getUuid());

    assertThat(updateResponse.memberUuid()).isEqualTo(profileResponse.memberUuid());
    assertThat(updateResponse.email()).isEqualTo(profileResponse.email());
    assertThat(updateResponse.nickname()).isEqualTo(profileResponse.nickname()).isEqualTo("새닉네임");
    assertThat(updateResponse.profileImageUrl())
        .isEqualTo(profileResponse.profileImageUrl())
        .isEqualTo("https://cdn.test/profile-images/new-image.png");
    assertThat(updateResponse.statusMessage())
        .isEqualTo(profileResponse.statusMessage())
        .isEqualTo("새 상태 메시지");
    assertThat(profileResponse.isHostEver()).isFalse();
    assertThat(profileResponse.hostedCrewCount()).isZero();
    assertThat(profileResponse.status()).isEqualTo(MemberStatus.ACTIVE);
    assertThat(profileResponse.createdAt()).isNotNull();
  }

  @TestConfiguration
  static class TestProfileImageUrlResolverConfig {

    @Bean
    ProfileImageUrlResolver profileImageUrlResolver() {
      return objectPath -> "https://cdn.test/" + objectPath;
    }
  }
}
