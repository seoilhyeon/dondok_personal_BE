package com.oit.dondok.infra.loadtest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("prod & load-test")
public class LoadTestProfileGuard {
  @Bean
  Object loadTestIsNeverProduction() {
    throw new IllegalStateException("load-test profile must not be combined with prod");
  }
}
