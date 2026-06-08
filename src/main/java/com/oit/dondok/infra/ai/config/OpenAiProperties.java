package com.oit.dondok.infra.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(String apiKey, String model) {

  private static final String DEFAULT_MODEL = "gpt-4.1-mini";

  public OpenAiProperties {
    model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
  }
}
