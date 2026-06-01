package com.oit.dondok.infra.image.service;

import com.oit.dondok.domain.member.port.ProfileImageUrlResolver;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "dev", "test", "integration"})
public class FakeProfileImageUrlResolver implements ProfileImageUrlResolver {

  @Override
  public String resolveProfileImageUrl(String profileImageS3Key) {
    // TODO(S3): Replace this non-prod fallback with an actual profile image URL resolver.
    return null;
  }
}
