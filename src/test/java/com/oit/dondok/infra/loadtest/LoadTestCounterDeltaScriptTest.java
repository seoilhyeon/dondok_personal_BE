package com.oit.dondok.infra.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoadTestCounterDeltaScriptTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @TempDir Path temporaryDirectory;

  @Test
  void writesExactSettlementSuccessAndFailureCounterDeltas() throws Exception {
    Path beforeSuccess = writeCounter("before-success.json", "2");
    Path afterSuccess = writeCounter("after-success.json", "102");
    Path beforeFailure = writeCounter("before-failure.json", "3");
    Path afterFailure = writeCounter("after-failure.json", "3");
    Path output = temporaryDirectory.resolve("counter-delta.json");

    Process process =
        counterDeltaProcess("100", beforeSuccess, afterSuccess, beforeFailure, afterFailure, output)
            .start();

    assertThat(process.waitFor()).isZero();
    Map<String, Object> evidence =
        objectMapper.readValue(output.toFile(), new TypeReference<>() {});
    assertThat(evidence)
        .containsEntry("expectedSuccessDelta", 100)
        .containsEntry("successDelta", 100)
        .containsEntry("failureDelta", 0);
  }

  @Test
  void rejectsUnexpectedSettlementCounterDelta() throws Exception {
    Path beforeSuccess = writeCounter("before-success.json", "0");
    Path afterSuccess = writeCounter("after-success.json", "99");
    Path beforeFailure = writeCounter("before-failure.json", "0");
    Path afterFailure = writeCounter("after-failure.json", "1");
    Path output = temporaryDirectory.resolve("counter-delta.json");

    Process process =
        counterDeltaProcess("100", beforeSuccess, afterSuccess, beforeFailure, afterFailure, output)
            .redirectErrorStream(true)
            .start();

    String outputText = new String(process.getInputStream().readAllBytes());
    assertThat(process.waitFor()).isNotZero();
    assertThat(outputText).contains("expected success delta 100 and failure delta 0");
    assertThat(output).doesNotExist();
  }

  private Path writeCounter(String filename, String value) throws Exception {
    Path path = temporaryDirectory.resolve(filename);
    Files.writeString(path, "[{\"metric\":{},\"value\":[0,\"" + value + "\"]}]");
    return path;
  }

  private ProcessBuilder counterDeltaProcess(
      String expected,
      Path beforeSuccess,
      Path afterSuccess,
      Path beforeFailure,
      Path afterFailure,
      Path output) {
    return new ProcessBuilder(
        "python3",
        Path.of("scripts", "verify-settlement-counter-delta.py").toString(),
        expected,
        beforeSuccess.toString(),
        afterSuccess.toString(),
        beforeFailure.toString(),
        afterFailure.toString(),
        output.toString());
  }
}
