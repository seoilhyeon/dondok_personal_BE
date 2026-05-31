package com.oit.dondok.infrastructure.auth.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.dto.response.ErrorResponse;
import com.oit.dondok.infrastructure.auth.exception.SecurityErrorCode;
import com.oit.dondok.infrastructure.auth.filter.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  public SecurityErrorHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  // 인증되지 않은 요청에 대해 401 JSON 응답을 반환한다.
  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {

    ErrorResponse errorResponse = resolveAuthenticationErrorResponse(request);
    writeErrorResponse(response, errorResponse);
  }

  // 인증은 되었지만 권한이 부족한 요청에 대해 403 JSON 응답을 반환한다.
  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {

    ErrorResponse errorResponse = ErrorResponse.error(SecurityErrorCode.ACCESS_DENIED);
    writeErrorResponse(response, errorResponse);
  }

  // JWT 필터에서 저장한 예외가 있으면 해당 에러를 사용하고, 없으면 기본 인증 실패 응답을 사용한다.
  private ErrorResponse resolveAuthenticationErrorResponse(HttpServletRequest request) {
    Object exception = request.getAttribute(JwtAuthenticationFilter.JWT_EXCEPTION_ATTRIBUTE);

    if (exception instanceof CustomException customException) {
      return ErrorResponse.error(customException);
    }

    return ErrorResponse.error(SecurityErrorCode.UNAUTHORIZED);
  }

  // ErrorResponse를 JSON 응답으로 작성한다.
  private void writeErrorResponse(HttpServletResponse response, ErrorResponse errorResponse)
      throws IOException {

    response.setStatus(errorResponse.status().value());
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getWriter(), errorResponse);
  }
}
