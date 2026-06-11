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

  // reEncode fast-path 전용 bounded executor. Spring Boot 기본 executor(applicationTaskExecutor)를
  // 덮지 않도록 별도 이름·스레드 prefix로 분리한다(listener가 @Qualifier로 이 빈을 명시 주입).
  // 포화 시 AbortPolicy로 거부하고, 제출 측(listener)이 거부를 swallow한다(배치 backstop이 처리).
  //
  // 단일 worker(core=max=1)로 제한한다. result finalize(complete/fail)가 행 잠금/조건부 UPDATE 없이
  // read-check(attempt_version)-write 구조라, fast-path가 병렬화되면 stale finalize race 여지가 남기 때문이다.
  // 이 전제는 "단일 app instance + scheduler가 한 인스턴스에서만 실행"일 때만 유효하다.
  // worker 병렬화 또는 다중 인스턴스 scheduler 운영 전에는 finalize를 DB conditional UPDATE로 원자화해야 한다.
  @Bean("reEncodeTaskExecutor")
  public TaskExecutor reEncodeTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("reencode-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }

  // FCM 발송 전용 bounded executor. I/O-bound 특성을 고려해 멀티 worker를 허용한다.
  // 포화 시 AbortPolicy로 거부하고, 제출 측(FcmSendEventListener)이 거부를 swallow한다(best-effort).
  @Bean("fcmTaskExecutor")
  public TaskExecutor fcmTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(200);
    executor.setThreadNamePrefix("fcm-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }
}
