package com.oit.dondok.infra.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LoadTestPointComparisonScriptTest {
  private static final List<String> PANELS =
      List.of(
          "HTTP RPS",
          "HTTP errors (4xx/5xx)",
          "HTTP p95",
          "HTTP p99",
          "Process CPU",
          "JVM total memory used",
          "JVM heap used / max",
          "JVM GC pause activity",
          "Hikari active / idle",
          "Hikari pending / max",
          "Datasource connection evidence",
          "Point charge success/failure rate",
          "Point charge p95");

  private final ObjectMapper objectMapper = new ObjectMapper();

  @TempDir Path temporaryDirectory;

  @Test
  void aggregatesThreeValidPointLimitRuns() throws Exception {
    Path first = writeRun("first", 100, 150);
    Path second = writeRun("second", 120, 170);
    Path third = writeRun("third", 110, 160);

    Process process = comparisonProcess(first, second, third).start();
    String output = new String(process.getInputStream().readAllBytes());

    assertThat(process.waitFor()).isZero();
    Map<String, Object> aggregate = objectMapper.readValue(output, new TypeReference<>() {});
    assertThat(aggregate)
        .containsEntry("phase", "point-limit-40")
        .containsEntry("runs", 3)
        .containsEntry("totalPointRequests", 30)
        .containsEntry("totalHttpFailures", 0)
        .containsEntry("totalCheckFailures", 0)
        .containsEntry("totalDroppedIterations", 0)
        .containsEntry("p95MedianMs", 110)
        .containsEntry("p95MaxMs", 120)
        .containsEntry("maxDurationMs", 170)
        .containsEntry("observabilityPanelsVerified", 13);
    assertThat(output).doesNotContain(temporaryDirectory.toString()).doesNotContain("first");
  }

  @Test
  void rejectsAnythingOtherThanThreeExplicitRuns() throws Exception {
    Process process =
        new ProcessBuilder(
                "python3", "-B", Path.of("scripts", "compare-point-load-runs.py").toString())
            .redirectErrorStream(true)
            .start();
    String output = new String(process.getInputStream().readAllBytes());

    assertThat(process.waitFor()).isNotZero();
    assertThat(output).isEqualTo("comparison: arguments\n");
  }

  @Test
  void rejectsUnsafeArtifactsBeforeParsingTheirSummary() throws Exception {
    Path first = writeRun("first", 100, 150);
    Path second = writeRun("second", 120, 170);
    Path third = writeRun("third", 110, 160);
    Map<String, Object> summary = readObject(second.resolve("summary.json"));
    summary.put("setup_data", "must-not-appear");
    writeJson(second.resolve("summary.json"), summary);

    Process process = comparisonProcess(first, second, third).start();
    String output = new String(process.getInputStream().readAllBytes());

    assertThat(process.waitFor()).isNotZero();
    assertThat(output).isEqualTo("run 2: artifact-safety\n").doesNotContain("must-not-appear");
  }

  @Test
  void rejectsInvalidTaggedMetricsWithoutUsingGlobalMetrics() throws Exception {
    Path first = writeRun("first", 100, 150);
    Path second = writeRun("second", 120, 170);
    Path third = writeRun("third", 110, 160);
    Map<String, Object> summary = readObject(second.resolve("summary.json"));
    Map<String, Object> metrics = object(summary.get("metrics"));
    object(object(metrics.get("http_reqs{target:point_charge}")).get("values")).put("count", true);
    metrics.put("http_reqs", metric(Map.of("count", 999999)));
    writeJson(second.resolve("summary.json"), summary);

    Process process = comparisonProcess(first, second, third).start();
    String output = new String(process.getInputStream().readAllBytes());

    assertThat(process.waitFor()).isNotZero();
    assertThat(output).isEqualTo("run 2: summary-metric\n");
  }

  @Test
  void rejectsCrossRunMetadataAndGrafanaEvidenceMismatches() throws Exception {
    Path first = writeRun("first", 100, 150);
    Path second = writeRun("second", 120, 170);
    Path third = writeRun("third", 110, 160);
    Map<String, Object> manifest = readObject(third.resolve("manifest.json"));
    manifest.put("gitSha", "other-git-sha");
    writeJson(third.resolve("manifest.json"), manifest);

    Process process = comparisonProcess(first, second, third).start();
    String output = new String(process.getInputStream().readAllBytes());

    assertThat(process.waitFor()).isNotZero();
    assertThat(output).isEqualTo("comparison: metadata\n");
  }

  @Test
  void rejectsInvalidManifestContracts() throws Exception {
    Path first = writeRun("first", 100, 150);
    Path second = writeRun("second", 120, 170);
    Path third = writeRun("third", 110, 160);
    Map<String, Object> manifest = readObject(second.resolve("manifest.json"));
    manifest.put("gitDirty", true);
    writeJson(second.resolve("manifest.json"), manifest);

    Process process = comparisonProcess(first, second, third).start();
    String output = new String(process.getInputStream().readAllBytes());

    assertThat(process.waitFor()).isNotZero();
    assertThat(output).isEqualTo("run 2: manifest-contract\n");
  }

  @Test
  void ignoresGlobalMetricsWhenTaggedMetricsAreHealthy() throws Exception {
    Path first = writeRun("first", 100, 150);
    Path second = writeRun("second", 120, 170);
    Path third = writeRun("third", 110, 160);
    Map<String, Object> summary = readObject(second.resolve("summary.json"));
    object(summary.get("metrics")).put("http_reqs", metric(Map.of("count", 999999)));
    writeJson(second.resolve("summary.json"), summary);

    Process process = comparisonProcess(first, second, third).start();
    String output = new String(process.getInputStream().readAllBytes());

    assertThat(process.waitFor()).isZero();
    assertThat(readObjectText(output)).containsEntry("totalPointRequests", 30);
  }

  @Test
  void rejectsChangedGrafanaQueryIdentityAcrossRuns() throws Exception {
    Path first = writeRun("first", 100, 150);
    Path second = writeRun("second", 120, 170);
    Path third = writeRun("third", 110, 160);
    List<Map<String, Object>> evidence = readList(third.resolve("grafana-evidence.json"));
    evidence.get(0).put("querySha256", "b".repeat(64));
    writeJson(third.resolve("grafana-evidence.json"), evidence);

    Process process = comparisonProcess(first, second, third).start();
    String output = new String(process.getInputStream().readAllBytes());

    assertThat(process.waitFor()).isNotZero();
    assertThat(output).isEqualTo("comparison: grafana-evidence\n");
  }

  @Test
  void rejectsHugeJsonIntegerWithoutLeakingParserFailure() throws Exception {
    RunSet runs = runs();
    Map<String, Object> summary = readObject(runs.second().resolve("summary.json"));
    tagged(summary, "http_reqs{target:point_charge}").put("count", BigInteger.TEN.pow(5_000));
    writeJson(runs.second().resolve("summary.json"), summary);

    Process process = comparisonProcess(runs.first(), runs.second(), runs.third()).start();
    String output = new String(process.getInputStream().readAllBytes());

    assertThat(process.waitFor()).isNotZero();
    assertThat(output).isEqualTo("run 2: summary\n").doesNotContain("Traceback");
  }

  @Test
  void rejectsDeeplyNestedJsonWithoutLeakingParserFailure() throws Exception {
    RunSet runs = runs();
    Path summary = runs.second().resolve("summary.json");
    Files.writeString(summary, "[".repeat(1_500) + "0" + "]".repeat(1_500));

    Process process = recursionErrorProcess(summary).start();
    String output = new String(process.getInputStream().readAllBytes());

    assertThat(process.waitFor()).isNotZero();
    assertThat(output)
        .isEqualTo("run 2: summary\n")
        .doesNotContain("Traceback")
        .doesNotContain(temporaryDirectory.toString());
  }

  @Test
  void rejectsNonNumericGrafanaResponseData() throws Exception {
    Path first = writeRun("first", 100, 150);
    Path second = writeRun("second", 120, 170);
    Path third = writeRun("third", 110, 160);
    writeJson(
        third.resolve("grafana-0.json"),
        Map.of(
            "results",
            Map.of(
                "A",
                Map.of(
                    "frames",
                    List.of(
                        Map.of(
                            "schema", Map.of("fields", List.of(Map.of("type", "number"))),
                            "data", Map.of("values", List.of(List.of(true)))))))));

    Process process = comparisonProcess(first, second, third).start();
    String output = new String(process.getInputStream().readAllBytes());

    assertThat(process.waitFor()).isNotZero();
    assertThat(output).isEqualTo("run 3: grafana-response\n");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("taggedMetricMutations")
  void rejectsTaggedMetricContractViolations(
      String name, String detector, Consumer<Map<String, Object>> mutation) throws Exception {
    RunSet runs = runs();
    Map<String, Object> summary = readObject(runs.second().resolve("summary.json"));
    mutation.accept(summary);
    writeJson(runs.second().resolve("summary.json"), summary);

    Process process = comparisonProcess(runs.first(), runs.second(), runs.third()).start();
    String output = new String(process.getInputStream().readAllBytes());

    assertThat(process.waitFor()).isNotZero();
    assertThat(output)
        .isEqualTo("run 2: " + detector + "\n")
        .doesNotContain(temporaryDirectory.toString());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("grafanaMutations")
  void rejectsInvalidGrafanaEvidence(
      String name, String detector, BiConsumer<LoadTestPointComparisonScriptTest, RunSet> mutation)
      throws Exception {
    RunSet runs = runs();
    mutation.accept(this, runs);

    Process process = comparisonProcess(runs.first(), runs.second(), runs.third()).start();
    String output = new String(process.getInputStream().readAllBytes());

    assertThat(process.waitFor()).isNotZero();
    assertThat(output)
        .isEqualTo("run 3: " + detector + "\n")
        .doesNotContain(temporaryDirectory.toString())
        .doesNotContain("../")
        .doesNotContain("Traceback");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("manifestMutations")
  void rejectsInvalidManifestContracts(
      String name, String detector, Consumer<Map<String, Object>> mutation) throws Exception {
    RunSet runs = runs();
    Map<String, Object> manifest = readObject(runs.second().resolve("manifest.json"));
    mutation.accept(manifest);
    writeJson(runs.second().resolve("manifest.json"), manifest);

    Process process = comparisonProcess(runs.first(), runs.second(), runs.third()).start();
    String output = new String(process.getInputStream().readAllBytes());

    assertThat(process.waitFor()).isNotZero();
    assertThat(output).isEqualTo("run 2: " + detector + "\n");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidJsonFiles")
  void rejectsMissingOrMalformedJsonFiles(
      String name, String fileName, String content, String detector) throws Exception {
    RunSet runs = runs();
    Path file = runs.second().resolve(fileName);
    if (content == null) {
      Files.delete(file);
    } else {
      Files.writeString(file, content);
    }

    Process process = comparisonProcess(runs.first(), runs.second(), runs.third()).start();
    String output = new String(process.getInputStream().readAllBytes());

    assertThat(process.waitFor()).isNotZero();
    assertThat(output)
        .isEqualTo("run 2: " + detector + "\n")
        .doesNotContain("Traceback")
        .doesNotContain(temporaryDirectory.toString());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("summaryTopLevelMutations")
  void rejectsMissingOrMalformedRequiredSummaryTopLevelKeys(
      String name, Consumer<Map<String, Object>> mutation) throws Exception {
    RunSet runs = runs();
    Map<String, Object> summary = readObject(runs.second().resolve("summary.json"));
    mutation.accept(summary);
    writeJson(runs.second().resolve("summary.json"), summary);

    Process process = comparisonProcess(runs.first(), runs.second(), runs.third()).start();
    String output = new String(process.getInputStream().readAllBytes());

    assertThat(process.waitFor()).isNotZero();
    assertThat(output).isEqualTo("run 2: summary-shape\n");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("crossRunManifestMutations")
  void rejectsCrossRunManifestMismatches(
      String name, String detector, Consumer<Map<String, Object>> mutation) throws Exception {
    RunSet runs = runs();
    Map<String, Object> manifest = readObject(runs.third().resolve("manifest.json"));
    mutation.accept(manifest);
    writeJson(runs.third().resolve("manifest.json"), manifest);

    Process process = comparisonProcess(runs.first(), runs.second(), runs.third()).start();
    String output = new String(process.getInputStream().readAllBytes());

    assertThat(process.waitFor()).isNotZero();
    assertThat(output).isEqualTo(detector + "\n");
  }

  @Test
  void reportsWideFiniteLatencyVariationWithoutInventingAThreshold() throws Exception {
    Path first = writeRun("first", 1, 2);
    Path second = writeRun("second", 999_999, 1_000_000);
    Path third = writeRun("third", 100, 101);

    Process process = comparisonProcess(first, second, third).start();
    String output = new String(process.getInputStream().readAllBytes());

    assertThat(process.waitFor()).isZero();
    assertThat(readObjectText(output))
        .containsEntry("p95MedianMs", 100)
        .containsEntry("p95MaxMs", 999_999)
        .containsEntry("maxDurationMs", 1_000_000);
  }

  private static Stream<Arguments> taggedMetricMutations() {
    return Stream.of(
        taggedMetric(
            "zero point requests",
            "summary-count",
            summary -> tagged(summary, "http_reqs{target:point_charge}").put("count", 0)),
        taggedMetric(
            "fractional point request count",
            "summary-metric",
            summary -> tagged(summary, "http_reqs{target:point_charge}").put("count", 1.5)),
        taggedMetric(
            "counter exceeds JSON-safe integer range",
            "summary-metric",
            summary ->
                tagged(summary, "http_reqs{target:point_charge}").put("count", Long.MAX_VALUE)),
        taggedMetric(
            "request iteration mismatch",
            "summary-count",
            summary -> tagged(summary, "iterations").put("count", 9)),
        taggedMetric(
            "control count is not one",
            "summary-count",
            summary -> tagged(summary, "http_reqs{target:control}").put("count", 2)),
        taggedMetric(
            "dropped iterations",
            "summary-dropped",
            summary ->
                tagged(summary, "dropped_iterations{scenario:point_charge}").put("count", 1)),
        taggedMetric(
            "http failure rate",
            "summary-http-failure",
            summary -> tagged(summary, "http_req_failed{target:point_charge}").put("rate", 1)),
        taggedMetric(
            "http failure passes are failure count",
            "summary-http-failure",
            summary -> tagged(summary, "http_req_failed{target:point_charge}").put("passes", 1)),
        taggedMetric(
            "check rate",
            "summary-check",
            summary -> tagged(summary, "checks{target:point_charge}").put("rate", 0)),
        taggedMetric(
            "check failures",
            "summary-check",
            summary -> tagged(summary, "checks{target:point_charge}").put("fails", 1)),
        taggedMetric(
            "boolean duration",
            "summary-metric",
            summary ->
                tagged(summary, "http_req_duration{target:point_charge}").put("p(95)", true)),
        taggedMetric(
            "negative duration",
            "summary-metric",
            summary -> tagged(summary, "http_req_duration{target:point_charge}").put("max", -1)),
        taggedMetric(
            "non finite duration",
            "summary-metric",
            summary ->
                tagged(summary, "http_req_duration{target:point_charge}").put("max", Double.NaN)));
  }

  private static Stream<Arguments> invalidJsonFiles() {
    return Stream.of(
        Arguments.of("missing manifest", "manifest.json", null, "manifest"),
        Arguments.of("malformed manifest", "manifest.json", "{", "manifest"),
        Arguments.of("missing Grafana evidence", "grafana-evidence.json", null, "grafana-evidence"),
        Arguments.of(
            "malformed Grafana evidence", "grafana-evidence.json", "[", "grafana-evidence"),
        Arguments.of("missing Grafana response", "grafana-0.json", null, "grafana-path"),
        Arguments.of("malformed Grafana response", "grafana-0.json", "{", "grafana-response"));
  }

  private static Stream<Arguments> summaryTopLevelMutations() {
    return Stream.of(
        summaryTopLevel("missing metrics", summary -> summary.remove("metrics")),
        summaryTopLevel("missing options", summary -> summary.remove("options")),
        summaryTopLevel("missing root group", summary -> summary.remove("root_group")),
        summaryTopLevel("missing state", summary -> summary.remove("state")),
        summaryTopLevel("malformed metrics", summary -> summary.put("metrics", List.of())),
        summaryTopLevel("malformed options", summary -> summary.put("options", List.of())),
        summaryTopLevel("malformed root group", summary -> summary.put("root_group", List.of())),
        summaryTopLevel("malformed state", summary -> summary.put("state", List.of())));
  }

  private static Stream<Arguments> grafanaMutations() {
    return Stream.of(
        Arguments.of(
            "missing required panel",
            "grafana-evidence",
            (BiConsumer<LoadTestPointComparisonScriptTest, RunSet>)
                (test, runs) -> test.removeFirstEvidence(runs.third())),
        Arguments.of(
            "non-string query hash",
            "grafana-evidence",
            (BiConsumer<LoadTestPointComparisonScriptTest, RunSet>)
                (test, runs) -> test.changeFirstEvidence(runs.third(), "querySha256", 1)),
        Arguments.of(
            "duplicate panel ref",
            "grafana-evidence",
            (BiConsumer<LoadTestPointComparisonScriptTest, RunSet>)
                (test, runs) -> test.duplicateFirstEvidence(runs.third())),
        Arguments.of(
            "point panel is not authoritative",
            "grafana-evidence",
            (BiConsumer<LoadTestPointComparisonScriptTest, RunSet>)
                (test, runs) ->
                    test.changeFirstEvidence(runs.third(), "authoritativeForComparison", false)),
        Arguments.of(
            "point panel has no isolated window",
            "grafana-evidence",
            (BiConsumer<LoadTestPointComparisonScriptTest, RunSet>)
                (test, runs) ->
                    test.changeFirstEvidence(runs.third(), "selectorWindowIsolated", false)),
        Arguments.of(
            "point panel is empty",
            "grafana-evidence",
            (BiConsumer<LoadTestPointComparisonScriptTest, RunSet>)
                (test, runs) -> test.changeFirstEvidence(runs.third(), "empty", true)),
        Arguments.of(
            "evidence path traversal",
            "grafana-path",
            (BiConsumer<LoadTestPointComparisonScriptTest, RunSet>)
                (test, runs) -> test.changeFirstEvidence(runs.third(), "path", "../outside.json")),
        Arguments.of(
            "embedded NUL evidence path",
            "grafana-path",
            (BiConsumer<LoadTestPointComparisonScriptTest, RunSet>)
                (test, runs) -> test.changeFirstEvidence(runs.third(), "path", "invalid\0.json")),
        Arguments.of(
            "response path is not JSON",
            "grafana-path",
            (BiConsumer<LoadTestPointComparisonScriptTest, RunSet>)
                (test, runs) -> test.changeFirstEvidence(runs.third(), "path", "grafana-0.txt")),
        Arguments.of(
            "symlinked Grafana response",
            "artifact-safety",
            (BiConsumer<LoadTestPointComparisonScriptTest, RunSet>)
                (test, runs) -> test.replaceFirstResponseWithSymlink(runs.third())),
        Arguments.of(
            "missing Datasource evidence",
            "grafana-evidence",
            (BiConsumer<LoadTestPointComparisonScriptTest, RunSet>)
                (test, runs) ->
                    test.removeEvidence(runs.third(), "Datasource connection evidence")),
        Arguments.of(
            "datasource has no numeric value",
            "grafana-response",
            (BiConsumer<LoadTestPointComparisonScriptTest, RunSet>)
                LoadTestPointComparisonScriptTest::writeNonNumericDatasourceResponse),
        Arguments.of(
            "response ref id differs",
            "grafana-response",
            (BiConsumer<LoadTestPointComparisonScriptTest, RunSet>)
                (test, runs) -> test.changeFirstEvidence(runs.third(), "refId", "B")));
  }

  private static Stream<Arguments> manifestMutations() {
    return Stream.of(
        manifest("wrong phase", "manifest-contract", value -> value.put("phase", "point-smoke")),
        manifest("wrong rate", "manifest-contract", value -> value.put("rate", "41")),
        manifest("blank identity", "manifest-identity", value -> value.put("gitSha", " ")),
        manifest("k6 failure", "manifest-contract", value -> value.put("k6ExitStatus", 1)),
        manifest("reset failure", "manifest-contract", value -> value.put("resetExitStatus", 1)),
        manifest("non passed status", "manifest-contract", value -> value.put("status", "failed")),
        manifest(
            "failure stage",
            "manifest-contract",
            value -> value.put("failureStages", List.of("k6"))),
        manifest(
            "selector not isolated",
            "manifest-contract",
            value -> value.put("selectorWindowIsolated", false)),
        manifest(
            "window is not sixty seconds", "manifest-window", value -> value.put("toMs", 60_999)));
  }

  private static Stream<Arguments> crossRunManifestMutations() {
    return Stream.of(
        Arguments.of(
            "app image",
            "comparison: metadata",
            (Consumer<Map<String, Object>>) value -> value.put("appImageId", "other-app")),
        Arguments.of(
            "k6 image",
            "comparison: metadata",
            (Consumer<Map<String, Object>>) value -> value.put("k6ImageId", "other-k6")),
        Arguments.of(
            "compose hash",
            "comparison: metadata",
            (Consumer<Map<String, Object>>)
                value -> value.put("composeConfigSha256", "other-compose")),
        Arguments.of(
            "operating system",
            "comparison: metadata",
            (Consumer<Map<String, Object>>) value -> value.put("os", "OtherOS")),
        Arguments.of(
            "architecture",
            "comparison: metadata",
            (Consumer<Map<String, Object>>) value -> value.put("arch", "other-arch")),
        Arguments.of(
            "duplicate run id",
            "comparison: duplicate-run-id",
            (Consumer<Map<String, Object>>) value -> value.put("runId", "first")),
        Arguments.of(
            "profiles violate fixed contract",
            "run 3: manifest-contract",
            (Consumer<Map<String, Object>>) value -> value.put("profiles", List.of("local"))),
        Arguments.of(
            "resource limit violates fixed contract",
            "run 3: manifest-contract",
            (Consumer<Map<String, Object>>) value -> value.put("appCpuLimit", "1.0")),
        Arguments.of(
            "scrape interval violates fixed contract",
            "run 3: manifest-contract",
            (Consumer<Map<String, Object>>) value -> value.put("scrapeInterval", "30s")),
        Arguments.of(
            "parameter violates fixed contract",
            "run 3: manifest-contract",
            (Consumer<Map<String, Object>>) value -> value.put("accounts", "21")));
  }

  private ProcessBuilder comparisonProcess(Path... directories) {
    return new ProcessBuilder(
            "python3",
            "-B",
            Path.of("scripts", "compare-point-load-runs.py").toString(),
            directories[0].toString(),
            directories[1].toString(),
            directories[2].toString())
        .redirectErrorStream(true);
  }

  private ProcessBuilder recursionErrorProcess(Path path) {
    String probe =
        """
        import importlib.util, pathlib, sys
        spec = importlib.util.spec_from_file_location("comparison", sys.argv[1])
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        loads = module.json.loads
        def deeply_nested_loads(value, *args, **kwargs):
            if value.count("[") >= 1500:
                raise RecursionError
            return loads(value, *args, **kwargs)
        module.json.loads = deeply_nested_loads
        try:
            module.require_regular_json(pathlib.Path(sys.argv[2]), 2, "summary")
        except module.Detector as error:
            print(f"{error.scope}: {error.name}")
            raise SystemExit(1)
        """;
    return new ProcessBuilder(
            "python3",
            "-B",
            "-c",
            probe,
            Path.of("scripts", "compare-point-load-runs.py").toString(),
            path.toString())
        .redirectErrorStream(true);
  }

  private RunSet runs() throws Exception {
    return new RunSet(
        writeRun("first", 100, 150), writeRun("second", 120, 170), writeRun("third", 110, 160));
  }

  private Path writeRun(String runId, int p95, int max) throws Exception {
    Path directory = Files.createDirectory(temporaryDirectory.resolve(runId));
    writeJson(directory.resolve("manifest.json"), manifest(runId));
    writeJson(directory.resolve("summary.json"), summary(p95, max));
    writeEvidence(directory);
    return directory;
  }

  private Map<String, Object> manifest(String runId) {
    Map<String, Object> manifest = new LinkedHashMap<>();
    manifest.put("schemaVersion", 1);
    manifest.put("runId", runId);
    manifest.put("phase", "point-limit-40");
    manifest.put("scenario", "point-charge.js");
    manifest.put("fromMs", 1_000);
    manifest.put("toMs", 61_000);
    manifest.put("rate", "40");
    manifest.put("duration", "6m");
    manifest.put("preAllocatedVUs", "80");
    manifest.put("maxVUs", "200");
    manifest.put("accounts", "20");
    manifest.put("settlements", null);
    manifest.put("profiles", List.of("local", "observability", "load-test"));
    manifest.put("appCpuLimit", "2.0");
    manifest.put("appMemoryLimit", "2G");
    manifest.put("scrapeInterval", "15s");
    manifest.put("gitSha", "git-sha");
    manifest.put("gitDirty", false);
    manifest.put("appImageId", "app-image");
    manifest.put("k6ImageRef", "grafana/k6:0.54.0");
    manifest.put("k6ImageId", "k6-image");
    manifest.put("k6ExitStatus", 0);
    manifest.put("resetExitStatus", 0);
    manifest.put("composeConfigSha256", "compose-sha");
    manifest.put("os", "Linux");
    manifest.put("arch", "arm64");
    manifest.put("selectorWindowIsolated", true);
    manifest.put("status", "passed");
    manifest.put("failureStages", List.of());
    return manifest;
  }

  private Map<String, Object> summary(int p95, int max) {
    Map<String, Object> metrics = new LinkedHashMap<>();
    metrics.put("http_reqs{target:point_charge}", metric(Map.of("count", 10)));
    metrics.put("iterations", metric(Map.of("count", 10)));
    metrics.put("http_reqs{target:control}", metric(Map.of("count", 1)));
    metrics.put("http_req_duration{target:point_charge}", metric(Map.of("p(95)", p95, "max", max)));
    metrics.put("http_req_failed{target:point_charge}", metric(Map.of("rate", 0, "passes", 0)));
    metrics.put("checks{target:point_charge}", metric(Map.of("rate", 1, "fails", 0)));
    metrics.put("dropped_iterations{scenario:point_charge}", metric(Map.of("count", 0)));
    return Map.of(
        "metrics", metrics, "options", Map.of(), "root_group", Map.of(), "state", Map.of());
  }

  private Map<String, Object> metric(Map<String, Object> values) {
    return Map.of("values", values);
  }

  private static Arguments taggedMetric(
      String name, String detector, Consumer<Map<String, Object>> mutation) {
    return Arguments.of(name, detector, mutation);
  }

  private static Arguments manifest(
      String name, String detector, Consumer<Map<String, Object>> mutation) {
    return Arguments.of(name, detector, mutation);
  }

  private static Arguments summaryTopLevel(String name, Consumer<Map<String, Object>> mutation) {
    return Arguments.of(name, mutation);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> tagged(Map<String, Object> summary, String metric) {
    return (Map<String, Object>)
        ((Map<String, Object>) ((Map<String, Object>) summary.get("metrics")).get(metric))
            .get("values");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readObject(Path path) throws Exception {
    return objectMapper.readValue(path.toFile(), new TypeReference<>() {});
  }

  private Map<String, Object> readObjectText(String value) throws Exception {
    return objectMapper.readValue(value, new TypeReference<>() {});
  }

  private List<Map<String, Object>> readList(Path path) throws Exception {
    return objectMapper.readValue(path.toFile(), new TypeReference<>() {});
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> object(Object value) {
    return (Map<String, Object>) value;
  }

  private void removeFirstEvidence(Path directory) {
    removeEvidence(directory, "HTTP RPS");
  }

  private void removeEvidence(Path directory, String panel) {
    try {
      List<Map<String, Object>> evidence = readList(directory.resolve("grafana-evidence.json"));
      evidence.removeIf(entry -> entry.get("panel").equals(panel));
      writeJson(directory.resolve("grafana-evidence.json"), evidence);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private void duplicateFirstEvidence(Path directory) {
    try {
      List<Map<String, Object>> evidence = readList(directory.resolve("grafana-evidence.json"));
      evidence.add(new LinkedHashMap<>(evidence.get(0)));
      writeJson(directory.resolve("grafana-evidence.json"), evidence);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private void changeFirstEvidence(Path directory, String key, Object value) {
    try {
      List<Map<String, Object>> evidence = readList(directory.resolve("grafana-evidence.json"));
      evidence.get(0).put(key, value);
      writeJson(directory.resolve("grafana-evidence.json"), evidence);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private void replaceFirstResponseWithSymlink(Path directory) {
    try {
      Path response = directory.resolve("grafana-0.json");
      Path target = directory.resolve("linked-response.json");
      Files.move(response, target);
      Files.createSymbolicLink(response, target.getFileName());
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private void writeNonNumericDatasourceResponse(RunSet runs) {
    try {
      List<Map<String, Object>> evidence = readList(runs.third().resolve("grafana-evidence.json"));
      Map<String, Object> datasource =
          evidence.stream()
              .filter(entry -> entry.get("panel").equals("Datasource connection evidence"))
              .findFirst()
              .orElseThrow();
      writeJson(
          runs.third().resolve((String) datasource.get("path")),
          responseWithValues((String) datasource.get("refId"), List.of(true)));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private void writeEvidence(Path directory) throws Exception {
    List<Map<String, Object>> evidence =
        PANELS.stream().map(panel -> evidenceEntry(panel)).toList();
    for (Map<String, Object> entry : evidence) {
      writeJson(
          directory.resolve((String) entry.get("path")), response((String) entry.get("refId")));
    }
    writeJson(directory.resolve("grafana-evidence.json"), evidence);
  }

  private Map<String, Object> evidenceEntry(String panel) {
    String refId = "A";
    String path = "grafana-" + PANELS.indexOf(panel) + ".json";
    return Map.of(
        "panel",
        panel,
        "refId",
        refId,
        "path",
        path,
        "querySha256",
        "a".repeat(64),
        "empty",
        false,
        "selectorWindowIsolated",
        !panel.equals("Datasource connection evidence"),
        "authoritativeForComparison",
        !panel.equals("Datasource connection evidence"));
  }

  private Map<String, Object> response(String refId) {
    return responseWithValues(refId, List.of(1));
  }

  private Map<String, Object> responseWithValues(String refId, List<?> values) {
    return Map.of(
        "results",
        Map.of(
            refId,
            Map.of(
                "frames",
                List.of(
                    Map.of(
                        "schema", Map.of("fields", List.of(Map.of("type", "number"))),
                        "data", Map.of("values", List.of(values)))))));
  }

  private void writeJson(Path path, Object value) throws Exception {
    objectMapper.writeValue(path.toFile(), value);
  }

  private record RunSet(Path first, Path second, Path third) {}
}
