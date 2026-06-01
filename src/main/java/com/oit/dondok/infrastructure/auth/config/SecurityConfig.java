package com.oit.dondok.infrastructure.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oit.dondok.domain.auth.service.TokenProvider;
import com.oit.dondok.infrastructure.auth.filter.JwtAuthenticationFilter;
import com.oit.dondok.infrastructure.auth.handler.SecurityErrorHandler;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsUtils;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

  private static final List<String> ALLOWED_ORIGINS =
      List.of("http://localhost:3000", "https://dondok-fe.vercel.app");
  private static final List<String> ALLOWED_METHODS =
      List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS");
  private static final List<String> ALLOWED_HEADERS = List.of("*");
  private static final List<String> EXPOSED_HEADERS = List.of("Authorization");

  private static final String[] POST_PERMIT_ALL_PATTERNS = {
    "/api/member/signup", "/api/auth/login", "/api/auth/refresh"
  };

  private static final String[] PERMIT_ALL_PATTERNS = {
    "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/health"
  };

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtAuthenticationFilter jwtAuthenticationFilter,
      SecurityErrorHandler securityErrorHandler)
      throws Exception {
    disableSecurityBasic(http);
    configureCors(http);
    configureSessionManagement(http);
    configureExceptionHandling(http, securityErrorHandler);
    configureAuthorization(http);
    configureJwtAuthenticationFilter(http, jwtAuthenticationFilter);

    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public JwtAuthenticationFilter jwtAuthenticationFilter(TokenProvider tokenProvider) {
    return new JwtAuthenticationFilter(tokenProvider);
  }

  @Bean
  public SecurityErrorHandler securityErrorHandler(ObjectMapper objectMapper) {
    return new SecurityErrorHandler(objectMapper);
  }

  // 기본 인증 끄기
  private void disableSecurityBasic(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .formLogin(formLogin -> formLogin.disable())
        .httpBasic(httpBasic -> httpBasic.disable());
  }

  // 프론트와 백엔드가 다른 주소에서 동작할 때 요청 허용
  private void configureCors(HttpSecurity http) throws Exception {
    http.cors(
        cors ->
            cors.configurationSource(
                request -> {
                  CorsConfiguration config = new CorsConfiguration();
                  config.setAllowedOrigins(ALLOWED_ORIGINS);
                  config.setAllowedMethods(ALLOWED_METHODS);
                  config.setAllowedHeaders(ALLOWED_HEADERS);
                  config.setExposedHeaders(EXPOSED_HEADERS);
                  config.setAllowCredentials(true);
                  return config;
                }));
  }

  // 세션 허용 x
  private void configureSessionManagement(HttpSecurity http) throws Exception {
    http.sessionManagement(
        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
  }

  // 공개 API와 인증이 필요한 API를 구분한다.
  private void configureAuthorization(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
        auth -> {
          auth.requestMatchers(CorsUtils::isPreFlightRequest)
              .permitAll()
              .requestMatchers(HttpMethod.POST, POST_PERMIT_ALL_PATTERNS)
              .permitAll()
              .requestMatchers(PERMIT_ALL_PATTERNS)
              .permitAll()
              .anyRequest()
              .authenticated();
        });
  }

  // JWT 인증 필터를 UsernamePasswordAuthenticationFilter 앞에 등록한다.
  private void configureJwtAuthenticationFilter(
      HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) {
    http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
  }

  // Spring Security 인증/인가 실패 응답을 프로젝트 공통 JSON 형식으로 처리한다.
  private void configureExceptionHandling(
      HttpSecurity http, SecurityErrorHandler securityErrorHandler) throws Exception {
    http.exceptionHandling(
        exception ->
            exception
                .authenticationEntryPoint(securityErrorHandler)
                .accessDeniedHandler(securityErrorHandler));
  }
}
