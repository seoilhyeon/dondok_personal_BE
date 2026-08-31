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
    int k6FailureStageSelected = runner.indexOf("prior_stage=\"k6\"");
    int manifestWritten = runner.indexOf("write_manifest \"$prior_status\"");
    int k6StatusReturned = runner.lastIndexOf("exit \"$k6_status\"");

    assertThat(k6StatusCaptured).isGreaterThanOrEqualTo(0);
    assertThat(k6FailureStageSelected).isGreaterThan(k6StatusCaptured);
    assertThat(manifestWritten).isGreaterThan(k6FailureStageSelected);
    assertThat(k6StatusReturned).isGreaterThan(manifestWritten);
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
  void earlyManifestAndTerminalCleanupManifestCoverMissingSummaryCounterAndGrafanaFailures()
      throws Exception {
    String runner = Files.readString(Path.of("scripts/run-load-test.sh"));

    int skeletonWritten = runner.indexOf("mkdir -p \"$phase_dir\"\nwrite_manifest 0 || true");
    int cleanupManifest = runner.indexOf("write_manifest \"$cleanup_status\" || true");
    int missingSummaryExit = runner.indexOf("run_stage=\"k6-summary\"");
    int counterStage = runner.indexOf("run_stage=\"settlement-counter\"");
    int grafanaStage = runner.indexOf("run_stage=\"grafana\"");

    assertThat(skeletonWritten).isGreaterThanOrEqualTo(0);
    assertThat(cleanupManifest).isGreaterThanOrEqualTo(0);
    assertThat(missingSummaryExit).isGreaterThan(cleanupManifest);
    assertThat(counterStage).isGreaterThan(cleanupManifest);
    assertThat(grafanaStage).isGreaterThan(cleanupManifest);
    assertThat(runner)
        .contains(
            "manifest_status = 'running' if status == '0' and stage in ('initializing', 'artifact-safety') else ('passed' if status == '0' else 'failed')");
    assertThat(runner).contains("'failureStages': [stage] if manifest_status == 'failed' else []");
    assertThat(runner).contains("temporary.replace(path)");
  }

  @Test
  void completedK6FailureKeepsTheK6FailureStage() throws Exception {
    String runner = Files.readString(Path.of("scripts/run-load-test.sh"));

    int finalK6FailureStage = runner.indexOf("prior_stage=\"k6\"");
    int imageMetadataCollection = runner.indexOf("app_image_id=\"$(", finalK6FailureStage);
    int failureManifestWritten =
        runner.indexOf("write_manifest \"$prior_status\"", finalK6FailureStage);

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

  @Test
  void pointArtifactSafetyRunsAfterFinalMetadataAndBeforeSuccessReport() throws Exception {
    String runner = Files.readString(Path.of("scripts/run-load-test.sh"));

    int metadata = runner.indexOf("config_hash=\"$(docker compose");
    int safetyStage = runner.indexOf("run_stage=\"artifact-safety\"");
    int verifier = runner.indexOf("scripts/verify-load-test-artifact-safety.py");
    int passedReport = runner.indexOf("Load-test phase passed:");

    assertThat(metadata).isNotNegative();
    assertThat(safetyStage).isGreaterThan(metadata);
    assertThat(verifier).isGreaterThan(safetyStage);
    assertThat(passedReport).isGreaterThan(verifier);
  }

  @Test
  void pointWritesThePassedManifestOnlyAfterArtifactSafetySucceeds() throws Exception {
    String runner = Files.readString(Path.of("scripts/run-load-test.sh"));

    int metadata = runner.indexOf("config_hash=\"$(docker compose");
    int safetyStage = runner.indexOf("run_stage=\"artifact-safety\"");
    int verifier = runner.indexOf("if python3 scripts/verify-load-test-artifact-safety.py");
    int restore = runner.indexOf("run_stage=\"$prior_stage\"", verifier);
    int passedManifest = runner.indexOf("write_manifest \"$prior_status\"", restore);

    assertThat(safetyStage).isGreaterThan(metadata);
    assertThat(runner.substring(metadata, verifier))
        .doesNotContain("write_manifest \"$prior_status\"");
    assertThat(restore).isGreaterThan(verifier);
    assertThat(passedManifest).isGreaterThan(restore);
    assertThat(runner).contains("stage in ('initializing', 'artifact-safety')");
  }

  @Test
  void artifactSafetyFailureIsNotSuppressedOrReportedAsPassed() throws Exception {
    String runner = Files.readString(Path.of("scripts/run-load-test.sh"));
    int verifier = runner.indexOf("scripts/verify-load-test-artifact-safety.py");
    int passedReport = runner.indexOf("Load-test phase passed:");
    String scannerToReport = runner.substring(verifier, passedReport);

    assertThat(scannerToReport).doesNotContain("|| true");
    assertThat(scannerToReport).contains("exit \"$artifact_status\"");
  }

  @Test
  void scannerRestoresThePriorStageOnlyWhenItPasses() throws Exception {
    String runner = Files.readString(Path.of("scripts/run-load-test.sh"));
    int scanner = runner.indexOf("if python3 scripts/verify-load-test-artifact-safety.py");
    int restore = runner.indexOf("run_stage=\"$prior_stage\"", scanner);
    int failure = runner.indexOf("else", restore);
    int failureExit = runner.indexOf("exit \"$artifact_status\"", failure);

    assertThat(scanner).isNotNegative();
    assertThat(restore).isGreaterThan(scanner);
    assertThat(failure).isGreaterThan(restore);
    assertThat(failureExit).isGreaterThan(failure);
  }

  @Test
  void manifestProducerContainsOnlyFixedMetadataAndNoRawSensitivePayloadFields() throws Exception {
    String runner = Files.readString(Path.of("scripts/run-load-test.sh"));
    String manifestProducer =
        runner.substring(runner.indexOf("payload = {"), runner.indexOf("temporary.replace(path)"));

    assertThat(manifestProducer)
        .doesNotContain("accessToken")
        .doesNotContain("access_token")
        .doesNotContain("Authorization")
        .doesNotContain("Bearer")
        .doesNotContain("setup_data")
        .doesNotContain("memberUuid")
        .doesNotContain("member_uuid");
  }
}
