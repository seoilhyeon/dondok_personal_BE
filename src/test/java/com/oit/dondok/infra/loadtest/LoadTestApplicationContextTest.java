package com.oit.dondok.infra.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.infra.loadtest.controller.LoadTestFixtureController;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"test", "load-test"})
class LoadTestApplicationContextTest {

  @Autowired private ApplicationContext applicationContext;
  @Autowired private MeterRegistry meterRegistry;

  @Test
  void loadTestProfileDisablesSchedulersAndEnablesFixtureIngressInTheApplicationContext() {
    assertThat(applicationContext.getBeansOfType(ScheduledTaskHolder.class)).isEmpty();
    assertThat(applicationContext.getBean(LoadTestFixtureController.class)).isNotNull();
  }

  @Test
  void finalSettlementSuccessTimerExistsBeforeTheOneShotBatch() {
    assertThat(
            meterRegistry
                .find("dondok.settlement.batch.execution")
                .tags("batch_type", "final", "outcome", "success", "failure_code", "none")
                .timer())
        .isNotNull();
  }
}
