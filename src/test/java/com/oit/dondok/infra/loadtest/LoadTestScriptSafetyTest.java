package com.oit.dondok.infra.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LoadTestScriptSafetyTest {
  @Test
  void settlementScriptReportsMalformedResponsesAndTimestampsAsFailedChecks() throws Exception {
    String script = Files.readString(Path.of("load-test/k6/settlement-batch.js"));

    assertThat(script)
        .contains("let body = null")
        .contains("settlement response is JSON")
        .contains("settlement timestamps parsed");
  }

  @Test
  void composeRunsK6AsTheHostUserAndRunnerExportsHostIds() throws Exception {
    String compose = Files.readString(Path.of("compose.observability.yaml"));
    String runner = Files.readString(Path.of("scripts/run-load-test.sh"));

    assertThat(compose).contains("user: \"${HOST_UID:-1000}:${HOST_GID:-1000}\"");
    assertThat(runner)
        .contains("export HOST_UID=\"${HOST_UID:-$(id -u)}\"")
        .contains("export HOST_GID=\"${HOST_GID:-$(id -g)}\"");
  }
}
