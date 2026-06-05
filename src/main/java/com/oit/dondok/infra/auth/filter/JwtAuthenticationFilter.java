package com.oit.dondok.infra.auth.filter;

import com.oit.dondok.domain.auth.service.TokenPayload;
import com.oit.dondok.domain.auth.service.TokenProvider;
import com.oit.dondok.global.exception.CustomException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

  public static final String JWT_EXCEPTION_ATTRIBUTE = "JWT_EXCEPTION";

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final TokenProvider tokenProvider;

  public JwtAuthenticationFilter(TokenProvider tokenProvider) {
    this.tokenProvider = tokenProvider;
  }

  // 요청마다 Access Token을 확인하고, 유효하면 SecurityContext에 인증 정보를 저장한다.
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String token = extractAccessToken(request);

    if (token != null) {
      try {
        authenticate(token);
      } catch (CustomException exception) {
        SecurityContextHolder.clearContext();
        request.setAttribute(JWT_EXCEPTION_ATTRIBUTE, exception);
      }
    }

    filterChain.doFilter(request, response);
  }

  // Authorization 헤더에서 Bearer Access Token 값을 추출한다.
  private String extractAccessToken(HttpServletRequest request) {
    String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);

    if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
      return null;
    }

    return authorizationHeader.substring(BEARER_PREFIX.length());
  }

  // Access Token을 검증하고 memberUuid 기반 인증 객체를 생성해 SecurityContext에 저장한다.
  private void authenticate(String token) {
    TokenPayload payload = tokenProvider.parseAccessToken(token);

    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            payload.memberUuid(), null, Collections.emptyList());

    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
