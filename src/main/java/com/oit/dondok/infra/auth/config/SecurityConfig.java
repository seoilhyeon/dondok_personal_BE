package com.oit.dondok.infra.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oit.dondok.domain.auth.code.OAuth2LoginCodeStore;
import com.oit.dondok.domain.auth.service.OAuth2LoginService;
import com.oit.dondok.domain.auth.service.TokenProvider;
import com.oit.dondok.global.config.CookieProperties;
import com.oit.dondok.global.config.CorsProperties;
import com.oit.dondok.infra.auth.filter.CookieCsrfGuardFilter;
import com.oit.dondok.infra.auth.filter.JwtAuthenticationFilter;
import com.oit.dondok.infra.auth.handler.OAuth2LoginFailureHandler;
import com.oit.dondok.infra.auth.handler.OAuth2LoginSuccessHandler;
import com.oit.dondok.infra.auth.handler.SecurityErrorHandler;
import com.oit.dondok.infra.auth.oauth2.CookieOAuth2AuthorizationRequestRepository;
import com.oit.dondok.infra.auth.oauth2.OAuth2RedirectProperties;
import com.oit.dondok.infra.auth.token.JwtTokenProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsUtils;

@Configuration
@EnableConfigurationProperties({
  CorsProperties.class,
  CookieProperties.class,
  JwtTokenProperties.class,
  OAuth2RedirectProperties.class
})
@RequiredArgsConstructor
public class SecurityConfig {

  private static final List<String> ALLOWED_METHODS =
      List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS");
  private static final List<String> ALLOWED_HEADERS = List.of("*");
  private static final List<String> EXPOSED_HEADERS = List.of("Authorization");

  private static final String[] POST_PERMIT_ALL_PATTERNS = {
    "/api/member/signup", "/api/auth/login", "/api/auth/refresh", "/api/auth/oauth2/token"
  };

  private static final String[] GET_PERMIT_ALL_PATTERNS = {
    "/api/crews", "/actuator/prometheus", "/api/actuator/prometheus"
  };

  private static final String[] PERMIT_ALL_PATTERNS = {
    "/swagger-ui.html",
    "/swagger-ui/**",
    "/v3/api-docs/**",
    "/actuator/health",
    "/api/health",
    "/api/actuator/health/**",
    "/oauth2/**",
    "/login/oauth2/**"
  };

  private final CorsProperties corsProperties;

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtAuthenticationFilter jwtAuthenticationFilter,
      CookieCsrfGuardFilter cookieCsrfGuardFilter,
      SecurityErrorHandler securityErrorHandler,
      OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
      OAuth2LoginFailureHandler oAuth2LoginFailureHandler,
      AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository)
      throws Exception {
    disableSecurityBasic(http);
    configureCors(http);
    configureSessionManagement(http);
    configureExceptionHandling(http, securityErrorHandler);
    configureAuthorization(http);
    configureOAuth2Login(
        http, oAuth2LoginSuccessHandler, oAuth2LoginFailureHandler, authorizationRequestRepository);
    configureCookieCsrfGuardFilter(http, cookieCsrfGuardFilter);
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

  @Bean
  public CookieCsrfGuardFilter cookieCsrfGuardFilter(SecurityErrorHandler securityErrorHandler) {
    return new CookieCsrfGuardFilter(corsProperties.allowedOrigins(), securityErrorHandler);
  }

  @Bean
  public OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler(
      OAuth2LoginService oAuth2LoginService,
      OAuth2LoginCodeStore oAuth2LoginCodeStore,
      OAuth2RedirectProperties redirectProperties) {
    return new OAuth2LoginSuccessHandler(
        oAuth2LoginService, oAuth2LoginCodeStore, redirectProperties);
  }

  @Bean
  public OAuth2LoginFailureHandler oAuth2LoginFailureHandler(
      OAuth2RedirectProperties redirectProperties) {
    return new OAuth2LoginFailureHandler(redirectProperties);
  }

  @Bean
  public AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository(
      ObjectMapper objectMapper,
      JwtTokenProperties jwtTokenProperties,
      CookieProperties cookieProperties) {
    return new CookieOAuth2AuthorizationRequestRepository(
        objectMapper, jwtTokenProperties, cookieProperties);
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
                  config.setAllowedOrigins(corsProperties.allowedOrigins());
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
              .requestMatchers(HttpMethod.GET, GET_PERMIT_ALL_PATTERNS)
              .permitAll()
              .requestMatchers(PERMIT_ALL_PATTERNS)
              .permitAll();

          auth.anyRequest().authenticated();
        });
  }

  // JWT 인증 필터를 UsernamePasswordAuthenticationFilter 앞에 등록한다.
  private void configureJwtAuthenticationFilter(
      HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) {
    http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
  }

  /** OAuth2 로그인 성공/실패 처리와 인증 요청 저장소를 설정한다. */
  private void configureOAuth2Login(
      HttpSecurity http,
      OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
      OAuth2LoginFailureHandler oAuth2LoginFailureHandler,
      AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository)
      throws Exception {
    http.oauth2Login(
        oauth2 ->
            oauth2
                .authorizationEndpoint(
                    endpoint ->
                        endpoint.authorizationRequestRepository(authorizationRequestRepository))
                .successHandler(oAuth2LoginSuccessHandler)
                .failureHandler(oAuth2LoginFailureHandler));
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

  private void configureCookieCsrfGuardFilter(
      HttpSecurity http, CookieCsrfGuardFilter cookieCsrfGuardFilter) {
    http.addFilterBefore(cookieCsrfGuardFilter, UsernamePasswordAuthenticationFilter.class);
  }
}
