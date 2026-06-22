package com.oit.dondok.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class CookieProfileTest {

  @Configuration
  @EnableConfigurationProperties(CookieProperties.class)
  static class TestConfig {}

  // 초기화 설정을 추가하여 실제 application-*.yml 파일을 읽어오도록 설정
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withInitializer(new ConfigDataApplicationContextInitializer())
          .withUserConfiguration(TestConfig.class);

  @Test
  @DisplayName("로컬 설정: application-local.yml 파일의 값을 바인딩한다")
  void localConfigCheck() {
    contextRunner
        .withPropertyValues("spring.profiles.active=local") // 프로파일만 지정
        .run(
            context -> {
              CookieProperties props = context.getBean(CookieProperties.class);
              // 실제 application-local.yml에 적힌 예상값과 비교
              assertThat(props.sameSite()).isEqualTo("Lax");
              assertThat(props.secure()).isFalse();
            });
  }

  @Test
  @DisplayName("운영 설정: application-prod.yml 파일의 기본값을 바인딩한다")
  void prodConfigCheck() {
    contextRunner
        .withPropertyValues(
            "spring.profiles.active=prod", "COOKIE_SECURE=true", "COOKIE_SAME_SITE=None")
        .run(
            context -> {
              CookieProperties props = context.getBean(CookieProperties.class);
              assertThat(props.sameSite()).isEqualTo("None");
              assertThat(props.secure()).isTrue();
            });
  }

  @Test
  @DisplayName("운영 설정: 쿠키 환경변수 값으로 기본값을 덮어쓴다")
  void prodConfigBindsCookieEnvironmentOverrides() {
    contextRunner
        .withPropertyValues(
            "spring.profiles.active=prod", "COOKIE_SECURE=false", "COOKIE_SAME_SITE=Lax")
        .run(
            context -> {
              CookieProperties props = context.getBean(CookieProperties.class);
              assertThat(props.sameSite()).isEqualTo("Lax");
              assertThat(props.secure()).isFalse();
            });
  }
}
