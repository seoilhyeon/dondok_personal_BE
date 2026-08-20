package com.oit.dondok.infra.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LoadTestRunnerArtifactFailureTest {
  @Test
  void completedK6ThresholdFailureIsReturnedOnlyAfterManifestCollection() throws Exception {
    String runner = Files.readString(Path.of("scripts/run-load-test.sh"));

    int k6StatusCaptured = runner.indexOf("k6_status=$?");
    int manifestWritten = runner.indexOf("'k6ExitStatus': int(k6_status)");
    int k6StatusReturned = runner.lastIndexOf("exit \"$k6_status\"");

    assertThat(k6StatusCaptured).isGreaterThanOrEqualTo(0);
    assertThat(manifestWritten).isGreaterThan(k6StatusCaptured);
    assertThat(k6StatusReturned).isGreaterThan(manifestWritten);
  }
}
