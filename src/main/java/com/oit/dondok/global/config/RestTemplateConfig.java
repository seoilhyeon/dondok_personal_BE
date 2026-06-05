package com.oit.dondok.global.config;

import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

  @Bean
  public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
        // 실무 필수 세팅: OpenAI API가 무한 먹통이 되어 내 서버까지 터지는 것을 방지하는 가드레일
        .setConnectTimeout(Duration.ofSeconds(10)) // 연결 최대 10초 대기
        .setReadTimeout(Duration.ofSeconds(30)) // AI 답변 최대 30초 대기 (GPT는 생각을 오래 할 수 있으므로 넉넉하게 줌)
        .build();
  }
}
