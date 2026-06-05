package com.oit.dondok.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.oit.dondok.domain.image.port.ImageDeliveryPort;
import com.oit.dondok.domain.image.port.ImageDeliveryUrl;
import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.member.dto.request.UpdateProfileRequest;
import com.oit.dondok.domain.member.dto.response.ProfileResponse;
import com.oit.dondok.domain.member.dto.response.ProfileUpdateResponse;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.entity.MemberStatus;
import com.oit.dondok.domain.member.exception.MemberErrorCode;
import com.oit.dondok.domain.member.repository.MemberProfileProjection;
import com.oit.dondok.domain.member.repository.MemberProfileQueryRepository;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import com.oit.dondok.infra.image.adapter.DefaultImageObjectKeyPolicy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemberProfileServiceTest {

  @Mock private MemberRepository memberRepository;
  @Mock private MemberProfileQueryRepository memberProfileQueryRepository;
  @Mock private ImageDeliveryPort imageDeliveryPort;
  @Spy private DefaultImageObjectKeyPolicy keyPolicy = new DefaultImageObjectKeyPolicy();

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
    given(
            imageDeliveryPort.createDeliveryUrl(
                new ImageObjectKey(profileImageS3Key), Duration.ofMinutes(10)))
        .willReturn(
            new ImageDeliveryUrl(
                profileImageUrl, OffsetDateTime.parse("2026-06-02T12:10:00+09:00")));

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
    then(imageDeliveryPort).shouldHaveNoInteractions();
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
    then(imageDeliveryPort).shouldHaveNoInteractions();
  }

  @Test
  void findProfileByMemberUuidThrowsWhenMemberProfileIsMissing() {
    UUID memberUuid = UUID.randomUUID();
    given(memberProfileQueryRepository.findByMemberUuid(memberUuid)).willReturn(Optional.empty());

    assertThatThrownBy(() -> memberProfileService.findProfileByMemberUuid(memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
    then(imageDeliveryPort).shouldHaveNoInteractions();
  }

  @Test
  void updateProfileUpdatesIncludedFieldsAndReturnsProfileUpdateResponse() {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c904");
    Member member = Member.create("member@example.com", "password-hash", "기존닉네임");
    ReflectionTestUtils.setField(member, "uuid", memberUuid);
    ReflectionTestUtils.setField(member, "updatedAt", LocalDateTime.of(2026, 6, 2, 11, 0));
    String profileImageS3Key =
        "profile/%s/11111111-1111-1111-1111-111111111111".formatted(memberUuid);
    String profileImageUrl =
        "https://cdn.example.com/profile/%s/11111111-1111-1111-1111-111111111111"
            .formatted(memberUuid);
    UpdateProfileRequest request =
        new UpdateProfileRequest(
            JsonNullable.of("새닉네임"), JsonNullable.of(profileImageS3Key), JsonNullable.of(null));
    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(member));
    given(memberRepository.existsByNicknameAndUuidNot("새닉네임", memberUuid)).willReturn(false);
    given(memberRepository.saveAndFlush(member)).willReturn(member);
    given(
            imageDeliveryPort.createDeliveryUrl(
                new ImageObjectKey(profileImageS3Key), Duration.ofMinutes(10)))
        .willReturn(
            new ImageDeliveryUrl(
                profileImageUrl, OffsetDateTime.parse("2026-06-02T12:10:00+09:00")));

    ProfileUpdateResponse response = memberProfileService.updateProfile(memberUuid, request);

    assertThat(member.getNickname()).isEqualTo("새닉네임");
    assertThat(member.getProfileImageS3Key()).isEqualTo(profileImageS3Key);
    assertThat(member.getStatusMessage()).isNull();
    assertThat(response.memberUuid()).isEqualTo(memberUuid);
    assertThat(response.email()).isEqualTo("member@example.com");
    assertThat(response.nickname()).isEqualTo("새닉네임");
    assertThat(response.profileImageUrl()).isEqualTo(profileImageUrl);
    assertThat(response.statusMessage()).isNull();
    assertThat(response.updatedAt()).isEqualTo(OffsetDateTime.parse("2026-06-02T11:00:00+09:00"));
    then(memberRepository).should().saveAndFlush(member);
  }

  @Test
  void updateProfileKeepsOmittedFieldsAndRemovesProfileImageWhenExplicitNullIsIncluded() {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c905");
    Member member = Member.create("member@example.com", "password-hash", "기존닉네임");
    ReflectionTestUtils.setField(member, "uuid", memberUuid);
    ReflectionTestUtils.setField(member, "updatedAt", LocalDateTime.of(2026, 6, 2, 11, 10));
    member.updateProfile("기존닉네임", "profile/old-image.png", "기존 상태 메시지");
    UpdateProfileRequest request =
        new UpdateProfileRequest(null, JsonNullable.of(null), JsonNullable.undefined());
    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(member));
    given(memberRepository.saveAndFlush(member)).willReturn(member);

    ProfileUpdateResponse response = memberProfileService.updateProfile(memberUuid, request);

    assertThat(member.getNickname()).isEqualTo("기존닉네임");
    assertThat(member.getProfileImageS3Key()).isNull();
    assertThat(member.getStatusMessage()).isEqualTo("기존 상태 메시지");
    assertThat(response.profileImageUrl()).isNull();
    assertThat(response.statusMessage()).isEqualTo("기존 상태 메시지");
    then(imageDeliveryPort).shouldHaveNoInteractions();
  }

  @Test
  void updateProfileThrowsInvalidInputWhenIncludedProfileImageKeyIsBlank() {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c907");
    Member member = Member.create("member@example.com", "password-hash", "기존닉네임");
    ReflectionTestUtils.setField(member, "uuid", memberUuid);
    ReflectionTestUtils.setField(member, "updatedAt", LocalDateTime.of(2026, 6, 2, 11, 20));
    member.updateProfile("기존닉네임", "profile/old-image.png", "기존 상태 메시지");
    UpdateProfileRequest request =
        new UpdateProfileRequest(null, JsonNullable.of("   "), JsonNullable.undefined());
    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(member));

    assertThatThrownBy(() -> memberProfileService.updateProfile(memberUuid, request))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.INVALID_INPUT);
    assertThat(member.getProfileImageS3Key()).isEqualTo("profile/old-image.png");
    then(memberRepository).should().findByUuid(memberUuid);
    then(memberRepository).shouldHaveNoMoreInteractions();
    then(imageDeliveryPort).shouldHaveNoInteractions();
  }

  @Test
  void updateProfileThrowsInvalidInputWhenIncludedProfileImageKeyUsesNonProfileNamespace() {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c908");
    Member member = Member.create("member@example.com", "password-hash", "기존닉네임");
    ReflectionTestUtils.setField(member, "uuid", memberUuid);
    member.updateProfile("기존닉네임", "profile/old-image.png", "기존 상태 메시지");
    String crewImageS3Key = "crew/%s/avatar.png".formatted(memberUuid);
    UpdateProfileRequest request =
        new UpdateProfileRequest(null, JsonNullable.of(crewImageS3Key), JsonNullable.undefined());
    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(member));

    assertThatThrownBy(() -> memberProfileService.updateProfile(memberUuid, request))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.INVALID_INPUT);
    assertThat(member.getProfileImageS3Key()).isEqualTo("profile/old-image.png");
    then(memberRepository).should().findByUuid(memberUuid);
    then(memberRepository).shouldHaveNoMoreInteractions();
    then(imageDeliveryPort).shouldHaveNoInteractions();
  }

  @Test
  void updateProfileThrowsInvalidInputWhenIncludedProfileImageKeyDoesNotMatchMemberUuid() {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c908");
    UUID otherMemberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c999");
    Member member = Member.create("member@example.com", "password-hash", "기존닉네임");
    ReflectionTestUtils.setField(member, "uuid", memberUuid);
    member.updateProfile("기존닉네임", "profile/old-image.png", "기존 상태 메시지");
    String otherMemberProfileImageS3Key =
        "profile/%s/11111111-1111-1111-1111-111111111111".formatted(otherMemberUuid);
    UpdateProfileRequest request =
        new UpdateProfileRequest(
            null, JsonNullable.of(otherMemberProfileImageS3Key), JsonNullable.undefined());
    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(member));

    assertThatThrownBy(() -> memberProfileService.updateProfile(memberUuid, request))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.INVALID_INPUT);
    assertThat(member.getProfileImageS3Key()).isEqualTo("profile/old-image.png");
    then(memberRepository).should().findByUuid(memberUuid);
    then(memberRepository).shouldHaveNoMoreInteractions();
    then(imageDeliveryPort).shouldHaveNoInteractions();
  }

  @Test
  void updateProfileThrowsInvalidInputWhenIncludedProfileImageKeyFileIdIsNotUuid() {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c908");
    Member member = Member.create("member@example.com", "password-hash", "기존닉네임");
    ReflectionTestUtils.setField(member, "uuid", memberUuid);
    member.updateProfile("기존닉네임", "profile/old-image.png", "기존 상태 메시지");
    String malformedProfileImageS3Key = "profile/%s/not-a-uuid".formatted(memberUuid);
    UpdateProfileRequest request =
        new UpdateProfileRequest(
            null, JsonNullable.of(malformedProfileImageS3Key), JsonNullable.undefined());
    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(member));

    assertThatThrownBy(() -> memberProfileService.updateProfile(memberUuid, request))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.INVALID_INPUT);
    assertThat(member.getProfileImageS3Key()).isEqualTo("profile/old-image.png");
    then(memberRepository).should().findByUuid(memberUuid);
    then(memberRepository).shouldHaveNoMoreInteractions();
    then(imageDeliveryPort).shouldHaveNoInteractions();
  }

  @Test
  void updateProfileThrowsInvalidInputWhenNoFieldIsIncluded() {
    UUID memberUuid = UUID.randomUUID();
    UpdateProfileRequest request = new UpdateProfileRequest(null, null, null);

    assertThatThrownBy(() -> memberProfileService.updateProfile(memberUuid, request))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.INVALID_INPUT);
    then(memberRepository).shouldHaveNoInteractions();
    then(imageDeliveryPort).shouldHaveNoInteractions();
  }

  @Test
  void updateProfileThrowsWhenMemberIsMissing() {
    UUID memberUuid = UUID.randomUUID();
    UpdateProfileRequest request = new UpdateProfileRequest(JsonNullable.of("새닉네임"), null, null);
    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.empty());

    assertThatThrownBy(() -> memberProfileService.updateProfile(memberUuid, request))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
    then(memberRepository).should().findByUuid(memberUuid);
    then(memberRepository).shouldHaveNoMoreInteractions();
    then(imageDeliveryPort).shouldHaveNoInteractions();
  }

  @Test
  void updateProfileThrowsWhenNicknameIsAlreadyUsedByAnotherMember() {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c906");
    Member member = Member.create("member@example.com", "password-hash", "기존닉네임");
    UpdateProfileRequest request = new UpdateProfileRequest(JsonNullable.of(" 새닉네임 "), null, null);
    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(member));
    given(memberRepository.existsByNicknameAndUuidNot("새닉네임", memberUuid)).willReturn(true);

    assertThatThrownBy(() -> memberProfileService.updateProfile(memberUuid, request))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MemberErrorCode.NICKNAME_ALREADY_EXISTS);
    then(memberRepository).should().findByUuid(memberUuid);
    then(memberRepository).should().existsByNicknameAndUuidNot("새닉네임", memberUuid);
    then(memberRepository).shouldHaveNoMoreInteractions();
    then(imageDeliveryPort).shouldHaveNoInteractions();
  }
}
