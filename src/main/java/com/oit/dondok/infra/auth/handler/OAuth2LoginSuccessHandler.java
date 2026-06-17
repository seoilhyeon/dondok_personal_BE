package com.oit.dondok.infra.auth.handler;

import com.oit.dondok.domain.auth.code.OAuth2LoginCodeStore;
import com.oit.dondok.domain.auth.exception.AuthErrorCode;
import com.oit.dondok.domain.auth.service.OAuth2LoginService;
import com.oit.dondok.domain.auth.service.OAuthUserInfo;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.infra.auth.oauth2.GoogleOAuth2UserInfo;
import com.oit.dondok.infra.auth.oauth2.OAuth2RedirectProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.util.UriComponentsBuilder;

@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

  private final OAuth2LoginService oAuth2LoginService;
  private final OAuth2LoginCodeStore oAuth2LoginCodeStore;
  private final OAuth2RedirectProperties redirectProperties;

  /** Google OAuth 인증 성공 후 회원을 확정하고 프론트 성공 페이지로 redirect한다. */
  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException, ServletException {
    try {
      UUID memberUuid = oAuth2LoginService.login(extractGoogleUserInfo(authentication));
      String code = oAuth2LoginCodeStore.issue(memberUuid);

      response.sendRedirect(successRedirectUri(code));
    } catch (CustomException exception) {
      response.sendRedirect(failureRedirectUri(exception.getErrorCode().getCode().toLowerCase()));
    } catch (Exception exception) {
      log.warn("Unexpected OAuth2 login success handling failure.", exception);
      response.sendRedirect(failureRedirectUri("oauth_login_failed"));
    }
  }

  /** Spring Security 인증 객체에서 Google 사용자 정보를 추출한다. */
  private OAuthUserInfo extractGoogleUserInfo(Authentication authentication) {
    if (!(authentication instanceof OAuth2AuthenticationToken token)
        || !"google".equals(token.getAuthorizedClientRegistrationId())) {
      throw new CustomException(AuthErrorCode.OAUTH_LOGIN_FAILED);
    }

    OAuth2User principal = token.getPrincipal();
    return new GoogleOAuth2UserInfo(principal.getAttributes()).toOAuthUserInfo();
  }

  /** 프론트 성공 페이지에 1회용 로그인 code를 붙여 redirect URL을 만든다. */
  private String successRedirectUri(String code) {
    return UriComponentsBuilder.fromUriString(redirectProperties.successRedirectUri())
        .queryParam("code", code)
        .build()
        .toUriString();
  }

  /** 프론트 실패 페이지에 실패 reason을 붙여 redirect URL을 만든다. */
  private String failureRedirectUri(String reason) {
    return UriComponentsBuilder.fromUriString(redirectProperties.failureRedirectUri())
        .queryParam("reason", reason)
        .build()
        .toUriString();
  }
}
