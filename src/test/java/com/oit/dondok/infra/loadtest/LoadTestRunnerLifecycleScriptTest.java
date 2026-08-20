package com.oit.dondok.infra.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoadTestRunnerLifecycleScriptTest {
  @TempDir Path temporaryDirectory;

  @Test
  void settlementEvidenceWaitCoversTwoPrometheusScrapes() throws Exception {
    Process process =
        new ProcessBuilder(
                "bash",
                "-c",
                "source scripts/load-test-lifecycle.sh; load_test_evidence_delay settlement-batch.js")
            .redirectErrorStream(true)
            .start();
    String output = new String(process.getInputStream().readAllBytes()).trim();

    assertThat(process.waitFor()).as(output).isZero();
    assertThat(output).isEqualTo("31");
  }

  @Test
  void cleanupStopsChildResetsFixtureAndReleasesExclusiveLock() throws Exception {
    Path fakeBin = Files.createDirectory(temporaryDirectory.resolve("bin"));
    Path resetLog = temporaryDirectory.resolve("reset.log");
    Path fakeCurl = fakeBin.resolve("curl");
    Files.writeString(fakeCurl, "#!/usr/bin/env bash\nprintf '%s\\n' \"$*\" >> \"$RESET_LOG\"\n");
    assertThat(fakeCurl.toFile().setExecutable(true)).isTrue();

    ProcessBuilder processBuilder =
        new ProcessBuilder(
            "bash",
            "-c",
            """
            set -euo pipefail
            source "$PROJECT_ROOT/scripts/load-test-lifecycle.sh"
            load_test_acquire_suite_lock "$TEST_ROOT/suite-lock" "pid=$$"
            if load_test_acquire_suite_lock "$TEST_ROOT/suite-lock" "duplicate"; then exit 10; fi
            sleep 60 &
            child_pid=$!
            set +e
            load_test_cleanup 7 "$child_pid" true "$TEST_ROOT/suite-lock" http://app:8080
            cleanup_status=$?
            set -e
            [[ "$cleanup_status" -eq 7 ]]
            ! kill -0 "$child_pid" 2>/dev/null
            [[ ! -e "$TEST_ROOT/suite-lock" ]]
            """);
    processBuilder.environment().put("PROJECT_ROOT", Path.of("").toAbsolutePath().toString());
    processBuilder.environment().put("TEST_ROOT", temporaryDirectory.toString());
    processBuilder.environment().put("RESET_LOG", resetLog.toString());
    processBuilder
        .environment()
        .put("PATH", fakeBin + System.getProperty("path.separator") + System.getenv("PATH"));
    processBuilder.redirectErrorStream(true);

    Process process = processBuilder.start();
    String processOutput = new String(process.getInputStream().readAllBytes());

    assertThat(process.waitFor()).as(processOutput).isZero();
    assertThat(Files.readString(resetLog))
        .contains("--request POST http://app:8080/api/load-test/reset");
  }
}
