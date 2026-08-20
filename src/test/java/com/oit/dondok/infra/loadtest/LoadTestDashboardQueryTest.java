package com.oit.dondok.infra.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LoadTestDashboardQueryTest {
  @Test
  void httpErrorPanelReturnsNumericZeroWhenNoErrorSeriesExist() throws Exception {
    String dashboard =
        Files.readString(
            Path.of("monitoring/grafana/provisioning/dashboards/point-settlement-baseline.json"));

    assertThat(dashboard).contains("status=~\\\"4..|5..\\\"}[1m])) or vector(0)\"");
  }
}
