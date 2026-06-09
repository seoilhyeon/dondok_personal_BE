package com.oit.dondok.global.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableCaching
public class CacheConfig {

  // 시스템 멤버 ID처럼 자주 읽고 거의 변하지 않는 값을 짧게 캐싱한다.
  @Bean("localCacheManager")
  @Primary
  public CacheManager cacheManager() {
    CaffeineCacheManager cacheManager = new CaffeineCacheManager("systemMemberId");
    cacheManager.setCaffeine(
        Caffeine.newBuilder().maximumSize(10).expireAfterWrite(1, TimeUnit.HOURS));
    return cacheManager;
  }
}
