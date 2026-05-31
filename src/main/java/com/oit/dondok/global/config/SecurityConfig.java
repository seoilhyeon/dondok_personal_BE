package com.oit.dondok.global.config;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
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
    "/api/auth/signup", "/api/auth/login", "/api/auth/refresh"
  };

  private static final String[] PERMIT_ALL_PATTERNS = {
    "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/health"
  };

  private static final String[] DEV_GET_PERMIT_ALL_PATTERNS = {"/api/me"};
  private static final Profiles DEV_BYPASS_PROFILES = Profiles.of("local", "dev");

  private final Environment environment;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    disableSecurityBasic(http);
    configureCors(http);
    configureSessionManagement(http);
    configureAuthorization(http);

    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
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

  // 열 API와 닫을 API 구분
  private void configureAuthorization(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
        auth -> {
          auth.requestMatchers(CorsUtils::isPreFlightRequest)
              .permitAll()
              .requestMatchers(HttpMethod.POST, POST_PERMIT_ALL_PATTERNS)
              .permitAll()
              .requestMatchers(PERMIT_ALL_PATTERNS)
              .permitAll();

          if (environment.acceptsProfiles(DEV_BYPASS_PROFILES)) {
            auth.requestMatchers(HttpMethod.GET, DEV_GET_PERMIT_ALL_PATTERNS).permitAll();
          }

          auth.anyRequest().authenticated();
        });
  }
}
