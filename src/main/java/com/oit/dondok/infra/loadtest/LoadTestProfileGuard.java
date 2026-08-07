package com.oit.dondok.infra.loadtest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration
@Profile("load-test")
public class LoadTestProfileGuard {
  @Bean
  Object loadTestProfileIsLocalOnly(Environment environment) {
    if (environment.matchesProfiles("prod")) {
      throw new IllegalStateException("load-test profile must not be combined with prod");
    }
    if (!environment.matchesProfiles("local") && !environment.matchesProfiles("test")) {
      throw new IllegalStateException("load-test profile must be combined with local outside test");
    }
    return new Object();
  }
}
