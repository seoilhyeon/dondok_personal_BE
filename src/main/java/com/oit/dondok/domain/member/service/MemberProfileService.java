package com.oit.dondok.domain.member.service;

import com.oit.dondok.domain.member.dto.request.UpdateProfileRequest;
import com.oit.dondok.domain.member.dto.response.ProfileResponse;
import com.oit.dondok.domain.member.dto.response.ProfileUpdateResponse;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.exception.MemberErrorCode;
import com.oit.dondok.domain.member.port.ProfileImageUrlResolver;
import com.oit.dondok.domain.member.repository.MemberProfileProjection;
import com.oit.dondok.domain.member.repository.MemberProfileQueryRepository;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MemberProfileService {

  private final MemberRepository memberRepository;
  private final MemberProfileQueryRepository memberProfileQueryRepository;
  private final ProfileImageUrlResolver profileImageUrlResolver;

  @Transactional(readOnly = true)
  public ProfileResponse findProfileByMemberUuid(UUID memberUuid) {
    MemberProfileProjection profile =
        memberProfileQueryRepository
            .findByMemberUuid(memberUuid)
            .orElseThrow(() -> new CustomException(MemberErrorCode.MEMBER_NOT_FOUND));

    return ProfileResponse.from(profile, resolveProfileImageUrl(profile.profileImageS3Key()));
  }

  @Transactional
  public ProfileUpdateResponse updateProfile(UUID memberUuid, UpdateProfileRequest request) {
    if (!request.hasAnyIncludedField()) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }

    Member member =
        memberRepository
            .findByUuid(memberUuid)
            .orElseThrow(() -> new CustomException(MemberErrorCode.MEMBER_NOT_FOUND));

    String nickname =
        request.includesNickname()
            ? normalizedNickname(memberUuid, request.nicknameValue())
            : member.getNickname();
    String profileImageS3Key =
        request.includesProfileImageS3Key()
            ? request.profileImageS3KeyValue()
            : member.getProfileImageS3Key();
    String statusMessage =
        request.includesStatusMessage() ? request.statusMessageValue() : member.getStatusMessage();

    member.updateProfile(nickname, profileImageS3Key, statusMessage);
    memberRepository.saveAndFlush(member);

    return ProfileUpdateResponse.from(member, resolveProfileImageUrl(profileImageS3Key));
  }

  private String normalizedNickname(UUID memberUuid, String nickname) {
    String trimmedNickname = nickname.trim();

    if (memberRepository.existsByNicknameAndUuidNot(trimmedNickname, memberUuid)) {
      throw new CustomException(MemberErrorCode.NICKNAME_ALREADY_EXISTS);
    }

    return trimmedNickname;
  }

  private String resolveProfileImageUrl(String profileImageS3Key) {
    if (!StringUtils.hasText(profileImageS3Key)) {
      return null;
    }

    return profileImageUrlResolver.resolveProfileImageUrl(profileImageS3Key);
  }
}
