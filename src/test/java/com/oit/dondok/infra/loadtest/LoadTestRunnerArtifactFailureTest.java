package com.oit.dondok.infra.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LoadTestRunnerArtifactFailureTest {
  @Test
  void k6FailureWritesTheExitTrapManifestBeforeReturningNonzero() throws Exception {
    String runner = Files.readString(Path.of("scripts/run-load-test.sh"));

    int k6StatusCaptured = runner.indexOf("k6_status=$?");
    int manifestWritten = runner.indexOf("write_manifest \"$status\" || true");
    int k6StatusReturned = runner.lastIndexOf("exit \"$k6_status\"");

    assertThat(k6StatusCaptured).isGreaterThanOrEqualTo(0);
    assertThat(manifestWritten).isGreaterThanOrEqualTo(0);
    assertThat(k6StatusReturned).isGreaterThan(k6StatusCaptured);
  }

  @Test
  void resetFailureIsRecordedBeforeTheRunnerReturns() throws Exception {
    String runner = Files.readString(Path.of("scripts/run-load-test.sh"));

    int resetStatusCaptured = runner.indexOf("reset_status=$?");
    int manifestWritten = runner.indexOf("write_manifest \"$reset_status\"");
    int resetFailureReturned = runner.indexOf("exit \"$reset_status\"");

    assertThat(resetStatusCaptured).isGreaterThanOrEqualTo(0);
    assertThat(manifestWritten).isGreaterThan(resetStatusCaptured);
    assertThat(resetFailureReturned).isGreaterThan(manifestWritten);
  }

  @Test
  void earlyManifestAndExitTrapCoverMissingSummaryCounterAndGrafanaFailures() throws Exception {
    String runner = Files.readString(Path.of("scripts/run-load-test.sh"));

    int skeletonWritten = runner.indexOf("mkdir -p \"$phase_dir\"\nwrite_manifest 0 || true");
    int exitTrapManifest = runner.indexOf("write_manifest \"$status\" || true");
    int missingSummaryExit = runner.indexOf("run_stage=\"k6-summary\"");
    int counterStage = runner.indexOf("run_stage=\"settlement-counter\"");
    int grafanaStage = runner.indexOf("run_stage=\"grafana\"");

    assertThat(skeletonWritten).isGreaterThanOrEqualTo(0);
    assertThat(exitTrapManifest).isGreaterThanOrEqualTo(0);
    assertThat(missingSummaryExit).isGreaterThan(exitTrapManifest);
    assertThat(counterStage).isGreaterThan(exitTrapManifest);
    assertThat(grafanaStage).isGreaterThan(exitTrapManifest);
    assertThat(runner)
        .contains(
            "manifest_status = 'running' if status == '0' and stage == 'initializing' else ('passed' if status == '0' else 'failed')");
    assertThat(runner).contains("'failureStages': [stage] if manifest_status == 'failed' else []");
    assertThat(runner).contains("temporary.replace(path)");
  }

  @Test
  void completedK6FailureKeepsTheK6FailureStage() throws Exception {
    String runner = Files.readString(Path.of("scripts/run-load-test.sh"));

    int finalK6FailureStage = runner.lastIndexOf("run_stage=\"k6\"");
    int imageMetadataCollection = runner.indexOf("app_image_id=\"$(", finalK6FailureStage);
    int failureManifestWritten =
        runner.indexOf("write_manifest \"$k6_status\"", finalK6FailureStage);

    assertThat(finalK6FailureStage).isGreaterThanOrEqualTo(0);
    assertThat(imageMetadataCollection).isGreaterThan(finalK6FailureStage);
    assertThat(failureManifestWritten).isGreaterThan(imageMetadataCollection);
  }

  @Test
  void runnerKeepsGrafanaPasswordOutOfCurlArguments() throws Exception {
    String runner = Files.readString(Path.of("scripts/run-load-test.sh"));

    assertThat(runner).doesNotContain("curl --fail --silent -u");
    assertThat(runner).contains("grafana_api_ready");
  }

  @Test
  void runnerNamesTheSettlementSnapshotDelay() throws Exception {
    String runner = Files.readString(Path.of("scripts/run-load-test.sh"));

    assertThat(runner).contains("settlement_before_snapshot_delay=315");
    assertThat(runner).contains("sleep \"$settlement_before_snapshot_delay\"");
  }
}
