package com.oit.dondok.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.oit.dondok.domain.member.dto.response.ProfileResponse;
import com.oit.dondok.domain.member.entity.MemberStatus;
import com.oit.dondok.domain.member.exception.MemberErrorCode;
import com.oit.dondok.domain.member.port.ProfileImageUrlResolver;
import com.oit.dondok.domain.member.repository.MemberProfileProjection;
import com.oit.dondok.domain.member.repository.MemberProfileQueryRepository;
import com.oit.dondok.global.exception.CustomException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberProfileServiceTest {

  @Mock private MemberProfileQueryRepository memberProfileQueryRepository;
  @Mock private ProfileImageUrlResolver profileImageUrlResolver;

  @InjectMocks private MemberProfileService memberProfileService;

  @Test
  void findProfileByMemberUuidReturnsProfileResponseWithResolvedProfileImageUrl() {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
    OffsetDateTime createdAt = OffsetDateTime.parse("2026-05-31T09:00:00+09:00");
    String profileImageS3Key = "profile/member.png";
    String profileImageUrl = "https://cdn.example.com/profile/member.png";
    MemberProfileProjection profile =
        new MemberProfileProjection(
            memberUuid,
            "host@example.com",
            "호스트",
            profileImageS3Key,
            "돈독하게 습관 형성 중",
            2L,
            MemberStatus.ACTIVE,
            createdAt);
    given(memberProfileQueryRepository.findByMemberUuid(memberUuid))
        .willReturn(Optional.of(profile));
    given(profileImageUrlResolver.resolveProfileImageUrl(profileImageS3Key))
        .willReturn(profileImageUrl);

    ProfileResponse response = memberProfileService.findProfileByMemberUuid(memberUuid);

    assertThat(response.memberUuid()).isEqualTo(memberUuid);
    assertThat(response.email()).isEqualTo("host@example.com");
    assertThat(response.nickname()).isEqualTo("호스트");
    assertThat(response.profileImageUrl()).isEqualTo(profileImageUrl);
    assertThat(response.statusMessage()).isEqualTo("돈독하게 습관 형성 중");
    assertThat(response.isHostEver()).isTrue();
    assertThat(response.hostedCrewCount()).isEqualTo(2L);
    assertThat(response.status()).isEqualTo(MemberStatus.ACTIVE);
    assertThat(response.createdAt()).isEqualTo(createdAt);
  }

  @Test
  void findProfileByMemberUuidReturnsNonHostProfileWhenHostedCrewCountIsZero() {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c902");
    OffsetDateTime createdAt = OffsetDateTime.parse("2026-05-31T09:00:00+09:00");
    MemberProfileProjection profile =
        new MemberProfileProjection(
            memberUuid,
            "member@example.com",
            "일반회원",
            null,
            null,
            0L,
            MemberStatus.ACTIVE,
            createdAt);
    given(memberProfileQueryRepository.findByMemberUuid(memberUuid))
        .willReturn(Optional.of(profile));

    ProfileResponse response = memberProfileService.findProfileByMemberUuid(memberUuid);

    assertThat(response.profileImageUrl()).isNull();
    assertThat(response.statusMessage()).isNull();
    assertThat(response.isHostEver()).isFalse();
    assertThat(response.hostedCrewCount()).isZero();
    then(profileImageUrlResolver).shouldHaveNoInteractions();
  }

  @Test
  void findProfileByMemberUuidDoesNotResolveProfileImageUrlWhenProfileImageKeyIsBlank() {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c903");
    OffsetDateTime createdAt = OffsetDateTime.parse("2026-05-31T09:00:00+09:00");
    MemberProfileProjection profile =
        new MemberProfileProjection(
            memberUuid,
            "blank-image@example.com",
            "이미지없음",
            "   ",
            null,
            0L,
            MemberStatus.ACTIVE,
            createdAt);
    given(memberProfileQueryRepository.findByMemberUuid(memberUuid))
        .willReturn(Optional.of(profile));

    ProfileResponse response = memberProfileService.findProfileByMemberUuid(memberUuid);

    assertThat(response.profileImageUrl()).isNull();
    then(profileImageUrlResolver).shouldHaveNoInteractions();
  }

  @Test
  void findProfileByMemberUuidThrowsWhenMemberProfileIsMissing() {
    UUID memberUuid = UUID.randomUUID();
    given(memberProfileQueryRepository.findByMemberUuid(memberUuid)).willReturn(Optional.empty());

    assertThatThrownBy(() -> memberProfileService.findProfileByMemberUuid(memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
    then(profileImageUrlResolver).shouldHaveNoInteractions();
  }
}
