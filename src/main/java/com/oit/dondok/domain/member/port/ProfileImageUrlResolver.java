package com.oit.dondok.domain.member.port;

public interface ProfileImageUrlResolver {
  String resolveProfileImageUrl(String profileImageS3Key);
}
