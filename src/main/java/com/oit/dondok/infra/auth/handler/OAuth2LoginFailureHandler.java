package com.oit.dondok.infra.auth.handler;

import com.oit.dondok.infra.auth.oauth2.OAuth2RedirectProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.web.util.UriComponentsBuilder;

@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

  private final OAuth2RedirectProperties redirectProperties;

  /** OAuth 인증 실패 시 프론트 실패 페이지로 redirect한다. */
  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException, ServletException {
    response.sendRedirect(failureRedirectUri());
  }

  /** 기본 OAuth 실패 reason이 포함된 redirect URL을 만든다. */
  private String failureRedirectUri() {
    return UriComponentsBuilder.fromUriString(redirectProperties.failureRedirectUri())
        .queryParam("reason", "oauth_login_failed")
        .build()
        .toUriString();
  }
}
