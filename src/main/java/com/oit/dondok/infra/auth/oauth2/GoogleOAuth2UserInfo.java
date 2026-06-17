package com.oit.dondok.infra.auth.oauth2;

import com.oit.dondok.domain.auth.service.OAuthUserInfo;
import com.oit.dondok.domain.member.entity.OAuthProvider;
import java.util.Map;

public class GoogleOAuth2UserInfo {

  private final Map<String, Object> attributes;

  public GoogleOAuth2UserInfo(Map<String, Object> attributes) {
    this.attributes = attributes;
  }

  /** Google OAuth 사용자 attributes를 도메인 사용자 정보로 변환한다. */
  public OAuthUserInfo toOAuthUserInfo() {
    return new OAuthUserInfo(
        OAuthProvider.GOOGLE,
        stringAttribute("sub"),
        stringAttribute("email"),
        booleanAttribute("email_verified"),
        stringAttribute("name"),
        stringAttribute("picture"));
  }

  /** 문자열 attributes를 안전하게 꺼낸다. */
  private String stringAttribute(String key) {
    Object value = attributes.get(key);
    if (value == null) {
      return null;
    }
    return value.toString();
  }

  /** boolean attributes를 안전하게 꺼낸다. */
  private boolean booleanAttribute(String key) {
    Object value = attributes.get(key);
    if (value instanceof Boolean booleanValue) {
      return booleanValue;
    }
    return Boolean.parseBoolean(String.valueOf(value));
  }
}
