package com.oit.dondok.infra.loadtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.oit.dondok.global.config.SchedulingConfiguration;
import com.oit.dondok.infra.loadtest.controller.LoadTestFixtureController;
import com.oit.dondok.infra.loadtest.service.LoadTestFixtureService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.config.ScheduledTaskHolder;

class LoadTestProfileBoundaryTest {

  @Test
  void loadTestDisablesSchedulersAndEnablesFixtureIngress() {
    try (AnnotationConfigApplicationContext context = contextWith("load-test")) {
      context.register(
          SchedulingConfiguration.class, LoadTestFixtureController.class, ScheduledProbe.class);
      context.registerBean(LoadTestFixtureService.class, () -> mock(LoadTestFixtureService.class));
      context.refresh();

      assertThat(context.containsBean("schedulingConfiguration")).isFalse();
      assertThat(context.containsBean("loadTestFixtureController")).isTrue();
      assertThat(context.getBeansOfType(ScheduledTaskHolder.class)).isEmpty();
    }
  }

  @Test
  void normalProfileEnablesSchedulersAndExcludesFixtureIngress() {
    try (AnnotationConfigApplicationContext context = contextWith()) {
      context.register(
          SchedulingConfiguration.class, LoadTestFixtureController.class, ScheduledProbe.class);
      context.refresh();

      assertThat(context.containsBean("schedulingConfiguration")).isTrue();
      assertThat(context.containsBean("loadTestFixtureController")).isFalse();
      assertThat(context.getBeansOfType(ScheduledTaskHolder.class).values())
          .anySatisfy(holder -> assertThat(holder.getScheduledTasks()).isNotEmpty());
    }
  }

  @Test
  void productionAndLoadTestProfilesFailClosed() {
    try (AnnotationConfigApplicationContext context = contextWith("prod", "load-test")) {
      context.register(LoadTestProfileGuard.class);

      assertThatThrownBy(context::refresh)
          .hasRootCauseMessage("load-test profile must not be combined with prod");
    }
  }

  static class ScheduledProbe {
    @Scheduled(fixedDelay = 60_000)
    void scheduled() {}
  }

  private AnnotationConfigApplicationContext contextWith(String... profiles) {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.getEnvironment().setActiveProfiles(profiles);
    return context;
  }
}
