package com.oit.dondok.domain.member.service;

import com.oit.dondok.domain.member.dto.response.ProfileResponse;
import com.oit.dondok.domain.member.exception.MemberErrorCode;
import com.oit.dondok.domain.member.port.ProfileImageUrlResolver;
import com.oit.dondok.domain.member.repository.MemberProfileProjection;
import com.oit.dondok.domain.member.repository.MemberProfileQueryRepository;
import com.oit.dondok.global.exception.CustomException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MemberProfileService {

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

  private String resolveProfileImageUrl(String profileImageS3Key) {
    if (!StringUtils.hasText(profileImageS3Key)) {
      return null;
    }

    return profileImageUrlResolver.resolveProfileImageUrl(profileImageS3Key);
  }
}
