package com.oit.dondok.global.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncSchedulingConfig {

  // reEncode fast-path 전용 bounded executor. 전역 기본 executor(applicationTaskExecutor)를 덮지 않도록
  // 별도 이름/스레드 prefix로 분리해, 다른 @Async 작업과 스레드명·정책이 섞이지 않게 한다.
  // 포화 시 AbortPolicy로 거부하고, 제출 측(listener)이 거부를 swallow한다(배치 backstop이 처리).
  @Bean("reEncodeTaskExecutor")
  public TaskExecutor reEncodeTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("reencode-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }
}
