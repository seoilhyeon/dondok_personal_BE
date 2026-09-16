package com.oit.dondok.infra.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class LoadTestRunnerLifecycleScriptTest {
  @TempDir Path temporaryDirectory;

  @Test
  void runnerLoadsLocalLoadTestEnvironmentFile() throws Exception {
    Path projectRoot = Files.createDirectory(temporaryDirectory.resolve("project"));
    Path scripts = Files.createDirectory(projectRoot.resolve("scripts"));
    Files.copy(
        Path.of("scripts/run-load-test.sh"),
        scripts.resolve("run-load-test.sh"),
        StandardCopyOption.REPLACE_EXISTING);
    Files.copy(
        Path.of("scripts/load-test-lifecycle.sh"),
        scripts.resolve("load-test-lifecycle.sh"),
        StandardCopyOption.REPLACE_EXISTING);
    Files.writeString(projectRoot.resolve(".env.load-test"), "GRAFANA_ADMIN_PASSWORD=local\n");

    ProcessBuilder processBuilder =
        new ProcessBuilder("bash", scripts.resolve("run-load-test.sh").toString(), "unknown");
    processBuilder.environment().remove("GRAFANA_ADMIN_PASSWORD");
    processBuilder.environment().remove("SPRING_PROFILES_ACTIVE");
    processBuilder.redirectErrorStream(true);

    Process process = processBuilder.start();
    String output = new String(process.getInputStream().readAllBytes());

    assertThat(process.waitFor()).isEqualTo(2);
    assertThat(output).contains("Unknown phase: unknown");
    assertThat(output).doesNotContain("Set GRAFANA_ADMIN_PASSWORD");
  }

  @Test
  void existingSuiteLockTakesPrecedenceOverPointComparisonCleanTreePreflight() throws Exception {
    Path projectRoot = createRunnerProject("stale-lock");
    Path lockDirectory = Files.createDirectories(projectRoot.resolve("load-test/.suite-lock"));
    Files.writeString(lockDirectory.resolve("owner"), "existing-owner\n");
    Path dockerLog = projectRoot.resolve("docker.log");
    Path fakeBin = createFakeDocker(projectRoot, dockerLog);

    ProcessResult result = runRunner(projectRoot, fakeBin, "point-limit-40");

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.output())
        .contains("inspect it instead of stealing it")
        .doesNotContain("clean committed Git tree");
    assertThat(lockDirectory.resolve("owner")).hasContent("existing-owner\n");
    assertThat(projectRoot.resolve("load-test/results")).doesNotExist();
    assertThat(dockerLog).doesNotExist();
  }

  @Test
  void danglingSuiteLockTakesPrecedenceOverPointComparisonCleanTreePreflight() throws Exception {
    Path projectRoot = createRunnerProject("dangling-lock");
    Path lock = projectRoot.resolve("load-test/.suite-lock");
    Files.createSymbolicLink(lock, Path.of("missing-lock"));
    Path dockerLog = projectRoot.resolve("docker.log");

    ProcessResult result =
        runRunner(projectRoot, createFakeDocker(projectRoot, dockerLog), "point-limit-40");

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.output())
        .contains("inspect it instead of stealing it")
        .doesNotContain("clean committed Git tree");
    assertThat(Files.isSymbolicLink(lock)).isTrue();
    assertThat(projectRoot.resolve("load-test/results")).doesNotExist();
    assertThat(dockerLog).doesNotExist();
  }

  @ParameterizedTest
  @ValueSource(strings = {"point-baseline", "point-limit-10", "point-limit-20", "point-limit-40"})
  void dirtyPointComparisonPhaseFailsBeforeAnySideEffect(String phase) throws Exception {
    Path projectRoot = createCommittedRunnerProject("dirty-" + phase);
    Files.writeString(projectRoot.resolve("untracked-marker"), "dirty\n");
    Path dockerLog = projectRoot.resolve("docker.log");
    Path fakeBin = createFakeDocker(projectRoot, dockerLog);

    ProcessResult result = runRunner(projectRoot, fakeBin, phase);

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.output())
        .contains("clean committed Git tree")
        .doesNotContain("untracked-marker");
    assertThat(projectRoot.resolve("load-test/results")).doesNotExist();
    assertThat(projectRoot.resolve("load-test/.suite-lock")).doesNotExist();
    assertThat(dockerLog).doesNotExist();
  }

  @Test
  void unavailableOrNonRepositoryGitFailsClosedBeforePointComparisonSideEffects() throws Exception {
    Path nonRepository = createRunnerProject("non-repository");
    Path nonRepositoryDockerLog = nonRepository.resolve("docker.log");
    ProcessResult nonRepositoryResult =
        runRunner(
            nonRepository,
            createFakeDocker(nonRepository, nonRepositoryDockerLog),
            "point-limit-40");

    Path unavailableGit = createCommittedRunnerProject("unavailable-git");
    Path unavailableGitDockerLog = unavailableGit.resolve("docker.log");
    Path fakeBin = createFakeDocker(unavailableGit, unavailableGitDockerLog);
    Files.writeString(
        fakeBin.resolve("git"), "#!/usr/bin/env bash\necho unavailable >&2\nexit 127\n");
    assertThat(fakeBin.resolve("git").toFile().setExecutable(true)).isTrue();
    ProcessResult unavailableGitResult = runRunner(unavailableGit, fakeBin, "point-limit-40");

    for (ProcessResult result : new ProcessResult[] {nonRepositoryResult, unavailableGitResult}) {
      assertThat(result.exitCode()).isNotZero();
      assertThat(result.output())
          .contains("clean committed Git tree")
          .doesNotContain("unavailable");
    }
    assertNoComparisonSideEffects(nonRepository, nonRepositoryDockerLog);
    assertNoComparisonSideEffects(unavailableGit, unavailableGitDockerLog);
  }

  @ParameterizedTest
  @ValueSource(strings = {"point-smoke", "settlement-smoke"})
  void dirtySmokeAndSettlementPhasesRemainOutsideTheCleanTreeGate(String phase) throws Exception {
    Path projectRoot = createCommittedRunnerProject("ungated-" + phase);
    Files.writeString(projectRoot.resolve("untracked-marker"), "dirty\n");
    Path dockerLog = projectRoot.resolve("docker.log");

    ProcessResult result = runRunner(projectRoot, createFakeDocker(projectRoot, dockerLog), phase);

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.output()).doesNotContain("clean committed Git tree");
    assertThat(dockerLog).exists();
  }

  @Test
  void cleanPointComparisonPhasePassesTheCleanTreePreflight() throws Exception {
    Path projectRoot = createCommittedRunnerProject("clean-comparison");
    Path dockerLog = projectRoot.resolve("docker.log");

    ProcessResult result =
        runRunner(projectRoot, createFakeDocker(projectRoot, dockerLog), "point-limit-40");

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.output()).doesNotContain("clean committed Git tree");
    assertThat(dockerLog).exists();
  }

  @ParameterizedTest
  @CsvSource({
    "point-baseline, --no-build",
    "point-limit-10, --no-build",
    "point-limit-20, --no-build",
    "point-limit-40, --no-build",
    "point-smoke, --build",
    "settlement-smoke, --build"
  })
  void runnerUsesTheExpectedTopologyImageMode(String phase, String imageMode) throws Exception {
    Path projectRoot = createCommittedRunnerProject("topology-" + phase);
    Path dockerLog = projectRoot.resolve("docker.log");

    ProcessResult result =
        runRunner(projectRoot, createTopologyDocker(projectRoot, dockerLog), phase);

    assertThat(result.exitCode()).isNotZero();
    String topologyCommand =
        Files.readAllLines(dockerLog).stream()
            .filter(command -> command.contains(" up -d "))
            .findFirst()
            .orElseThrow();
    assertThat(topologyCommand)
        .contains("up -d " + imageMode + " mysql redis localstack app")
        .doesNotContain(imageMode.equals("--build") ? "--no-build" : "--build");
  }

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
            [[ -d "$TEST_ROOT/suite-lock" ]]
            [[ "$(cat "$TEST_ROOT/suite-lock/owner")" = "pid=$$" ]]
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
        .contains("--fail")
        .contains("--connect-timeout")
        .contains("--max-time")
        .contains("--request POST http://app:8080/api/load-test/reset");
  }

  @Test
  void cleanupRetainsLockAndFailsWhenResetFails() throws Exception {
    Path fakeBin = Files.createDirectory(temporaryDirectory.resolve("failing-bin"));
    Path fakeCurl = fakeBin.resolve("curl");
    Files.writeString(fakeCurl, "#!/usr/bin/env bash\nexit 22\n");
    assertThat(fakeCurl.toFile().setExecutable(true)).isTrue();

    ProcessBuilder processBuilder =
        new ProcessBuilder(
            "bash",
            "-c",
            """
            set -euo pipefail
            source "$PROJECT_ROOT/scripts/load-test-lifecycle.sh"
            load_test_acquire_suite_lock "$TEST_ROOT/suite-lock" "pid=$$"
            set +e
            load_test_cleanup 0 "" true "$TEST_ROOT/suite-lock" http://app:8080
            cleanup_status=$?
            set -e
            [[ "$cleanup_status" -ne 0 ]]
            [[ -d "$TEST_ROOT/suite-lock" ]]
            [[ "$(cat "$TEST_ROOT/suite-lock/owner")" = "pid=$$" ]]
            """);
    processBuilder.environment().put("PROJECT_ROOT", Path.of("").toAbsolutePath().toString());
    processBuilder.environment().put("TEST_ROOT", temporaryDirectory.toString());
    processBuilder
        .environment()
        .put("PATH", fakeBin + System.getProperty("path.separator") + System.getenv("PATH"));
    processBuilder.redirectErrorStream(true);

    Process process = processBuilder.start();
    String processOutput = new String(process.getInputStream().readAllBytes());

    assertThat(process.waitFor()).as(processOutput).isZero();
  }

  @Test
  void runnerInstallsCleanupTrapOnlyAfterLockAcquisition() throws Exception {
    String runner = Files.readString(Path.of("scripts/run-load-test.sh"));
    int lockAcquisitionIndex = runner.indexOf("load_test_acquire_suite_lock");
    int cleanupTrapIndex = runner.indexOf("trap cleanup EXIT INT TERM");

    assertThat(lockAcquisitionIndex).isNotNegative();
    assertThat(cleanupTrapIndex).isNotNegative();
    assertThat(cleanupTrapIndex).isGreaterThan(lockAcquisitionIndex);
  }

  @Test
  void cleanupFailsWhenLockOwnerCannotBeRemoved() throws Exception {
    Path fakeBin = Files.createDirectory(temporaryDirectory.resolve("failing-rm-bin"));
    Path fakeRm = fakeBin.resolve("rm");
    Files.writeString(fakeRm, "#!/usr/bin/env bash\nexit 1\n");
    assertThat(fakeRm.toFile().setExecutable(true)).isTrue();
    Path lockDirectory = Files.createDirectory(temporaryDirectory.resolve("owner-failure-lock"));
    Files.writeString(lockDirectory.resolve("owner"), "pid=1\n");

    ProcessBuilder processBuilder =
        cleanupProcessBuilder(fakeBin, lockDirectory, "load_test_cleanup 0 \"\" false");
    Process process = processBuilder.start();
    String output = new String(process.getInputStream().readAllBytes());

    assertThat(process.waitFor()).isNotZero();
    assertThat(output).contains("Failed to remove suite lock owner");
    assertThat(lockDirectory.resolve("owner")).exists();
  }

  @Test
  void cleanupFailsWhenLockDirectoryCannotBeRemoved() throws Exception {
    Path fakeBin = Files.createDirectory(temporaryDirectory.resolve("failing-rmdir-bin"));
    Path fakeRm = fakeBin.resolve("rm");
    Files.writeString(fakeRm, "#!/usr/bin/env bash\n/bin/rm \"$@\"\n");
    assertThat(fakeRm.toFile().setExecutable(true)).isTrue();
    Path fakeRmdir = fakeBin.resolve("rmdir");
    Files.writeString(fakeRmdir, "#!/usr/bin/env bash\nexit 1\n");
    assertThat(fakeRmdir.toFile().setExecutable(true)).isTrue();
    Path lockDirectory =
        Files.createDirectory(temporaryDirectory.resolve("directory-failure-lock"));
    Files.writeString(lockDirectory.resolve("owner"), "pid=1\n");

    ProcessBuilder processBuilder =
        cleanupProcessBuilder(fakeBin, lockDirectory, "load_test_cleanup 0 \"\" false");
    Process process = processBuilder.start();
    String output = new String(process.getInputStream().readAllBytes());

    assertThat(process.waitFor()).isNotZero();
    assertThat(output).contains("Failed to remove suite lock directory");
    assertThat(lockDirectory).exists();
  }

  @Test
  void cleanupPreservesAnExistingArtifactSafetyFailureWhenResetAlsoFails() throws Exception {
    Path fakeBin =
        Files.createDirectory(temporaryDirectory.resolve("artifact-safety-reset-failure-bin"));
    Path fakeCurl = fakeBin.resolve("curl");
    Files.writeString(fakeCurl, "#!/usr/bin/env bash\nexit 22\n");
    assertThat(fakeCurl.toFile().setExecutable(true)).isTrue();
    Path lockDirectory = Files.createDirectory(temporaryDirectory.resolve("artifact-safety-lock"));
    Files.writeString(lockDirectory.resolve("owner"), "pid=1\n");

    Process process =
        cleanupProcessBuilder(fakeBin, lockDirectory, "load_test_cleanup 1 \"\" true").start();
    String output = new String(process.getInputStream().readAllBytes());

    assertThat(process.waitFor()).as(output).isEqualTo(1);
    assertThat(lockDirectory).exists();
  }

  @Test
  void cleanupPromotesASuccessfulRunToFailureWhenResetFails() throws Exception {
    Path fakeBin =
        Files.createDirectory(temporaryDirectory.resolve("successful-reset-failure-bin"));
    Path fakeCurl = fakeBin.resolve("curl");
    Files.writeString(fakeCurl, "#!/usr/bin/env bash\nexit 22\n");
    assertThat(fakeCurl.toFile().setExecutable(true)).isTrue();
    Path lockDirectory = Files.createDirectory(temporaryDirectory.resolve("successful-reset-lock"));
    Files.writeString(lockDirectory.resolve("owner"), "pid=1\n");

    Process process =
        cleanupProcessBuilder(fakeBin, lockDirectory, "load_test_cleanup 0 \"\" true").start();
    String output = new String(process.getInputStream().readAllBytes());

    assertThat(process.waitFor()).as(output).isEqualTo(1);
    assertThat(lockDirectory).exists();
  }

  @Test
  void runnerWritesTheTerminalManifestAfterCleanupDeterminesTheEffectiveStatus() throws Exception {
    String runner = Files.readString(Path.of("scripts/run-load-test.sh"));
    int cleanupCall = runner.indexOf("load_test_cleanup \"$status\"");
    int cleanupStatus = runner.indexOf("cleanup_status=$?", cleanupCall);
    int terminalManifest = runner.indexOf("write_manifest \"$cleanup_status\"", cleanupStatus);

    assertThat(cleanupCall).isNotNegative();
    assertThat(cleanupStatus).isGreaterThan(cleanupCall);
    assertThat(terminalManifest).isGreaterThan(cleanupStatus);
  }

  private ProcessBuilder cleanupProcessBuilder(
      Path fakeBin, Path lockDirectory, String cleanupCommand) {
    ProcessBuilder processBuilder =
        new ProcessBuilder(
            "bash",
            "-c",
            "source \"$PROJECT_ROOT/scripts/load-test-lifecycle.sh\"; "
                + cleanupCommand
                + " \"$LOCK_DIR\" http://app:8080");
    processBuilder.environment().put("PROJECT_ROOT", Path.of("").toAbsolutePath().toString());
    processBuilder.environment().put("LOCK_DIR", lockDirectory.toString());
    processBuilder
        .environment()
        .put("PATH", fakeBin + System.getProperty("path.separator") + System.getenv("PATH"));
    processBuilder.redirectErrorStream(true);
    return processBuilder;
  }

  private Path createRunnerProject(String name) throws Exception {
    Path projectRoot = Files.createDirectory(temporaryDirectory.resolve(name));
    Path scripts = Files.createDirectory(projectRoot.resolve("scripts"));
    Files.copy(
        Path.of("scripts/run-load-test.sh"),
        scripts.resolve("run-load-test.sh"),
        StandardCopyOption.REPLACE_EXISTING);
    Files.copy(
        Path.of("scripts/load-test-lifecycle.sh"),
        scripts.resolve("load-test-lifecycle.sh"),
        StandardCopyOption.REPLACE_EXISTING);
    Files.createDirectory(projectRoot.resolve("load-test"));
    Files.writeString(projectRoot.resolve(".env.load-test"), "GRAFANA_ADMIN_PASSWORD=local\n");
    return projectRoot;
  }

  private Path createCommittedRunnerProject(String name) throws Exception {
    Path projectRoot = createRunnerProject(name);
    runCommand(projectRoot, "git", "init", "-q");
    runCommand(projectRoot, "git", "config", "user.email", "load-test@example.com");
    runCommand(projectRoot, "git", "config", "user.name", "Load Test");
    runCommand(projectRoot, "git", "add", ".");
    runCommand(projectRoot, "git", "commit", "-qm", "initial");
    return projectRoot;
  }

  private Path createFakeDocker(Path projectRoot, Path dockerLog) throws Exception {
    Path fakeBin =
        Files.createDirectories(
            temporaryDirectory.resolve("fake-bin-" + projectRoot.getFileName()));
    Path fakeDocker = fakeBin.resolve("docker");
    Files.writeString(
        fakeDocker, "#!/usr/bin/env bash\nprintf 'docker called\\n' >> \"$DOCKER_LOG\"\nexit 99\n");
    assertThat(fakeDocker.toFile().setExecutable(true)).isTrue();
    return fakeBin;
  }

  private Path createTopologyDocker(Path projectRoot, Path dockerLog) throws Exception {
    Path fakeBin =
        Files.createDirectories(
            temporaryDirectory.resolve("topology-fake-bin-" + projectRoot.getFileName()));
    Path fakeDocker = fakeBin.resolve("docker");
    Files.writeString(
        fakeDocker,
        "#!/usr/bin/env bash\nprintf '%s\\n' \"$*\" >> \"$DOCKER_LOG\"\n"
            + "if [[ \" $* \" == *\" up -d \"* ]]; then exit 99; fi\n");
    assertThat(fakeDocker.toFile().setExecutable(true)).isTrue();
    return fakeBin;
  }

  private ProcessResult runRunner(Path projectRoot, Path fakeBin, String phase) throws Exception {
    ProcessBuilder processBuilder =
        new ProcessBuilder("bash", "scripts/run-load-test.sh", phase)
            .directory(projectRoot.toFile());
    processBuilder.environment().put("DOCKER_LOG", projectRoot.resolve("docker.log").toString());
    processBuilder
        .environment()
        .put("PATH", fakeBin + System.getProperty("path.separator") + System.getenv("PATH"));
    processBuilder.redirectErrorStream(true);
    Process process = processBuilder.start();
    String output = new String(process.getInputStream().readAllBytes());
    return new ProcessResult(process.waitFor(), output);
  }

  private void assertNoComparisonSideEffects(Path projectRoot, Path dockerLog) {
    assertThat(projectRoot.resolve("load-test/results")).doesNotExist();
    assertThat(projectRoot.resolve("load-test/.suite-lock")).doesNotExist();
    assertThat(dockerLog).doesNotExist();
  }

  private void runCommand(Path directory, String... command) throws Exception {
    Process process = new ProcessBuilder(command).directory(directory.toFile()).start();
    String output = new String(process.getErrorStream().readAllBytes());
    assertThat(process.waitFor()).as(output).isZero();
  }

  private record ProcessResult(int exitCode, String output) {}
}
