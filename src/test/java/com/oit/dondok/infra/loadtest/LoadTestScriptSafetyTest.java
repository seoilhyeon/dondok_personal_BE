package com.oit.dondok.infra.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoadTestScriptSafetyTest {
  @TempDir Path temporaryDirectory;

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

  @Test
  void pointHandleSummaryExcludesSetupDataAndKeepsTheSummaryObject() throws Exception {
    String script = Files.readString(Path.of("load-test/k6/point-charge.js"));

    assertThat(script)
        .contains("const { setup_data:")
        .contains("...summary } = data")
        .contains("JSON.stringify(summary)")
        .doesNotContain("JSON.stringify(data)");
  }

  @Test
  void safePointBundlePasses() throws Exception {
    Path bundle = Files.createDirectory(temporaryDirectory.resolve("point-smoke"));
    Files.writeString(
        bundle.resolve("summary.json"),
        "{\"metrics\":{},\"options\":{},\"root_group\":{},\"state\":{}}");
    Path nested = Files.createDirectories(bundle.resolve("nested"));
    Files.writeString(nested.resolve("k6.log"), "k6 completed");
    Files.writeString(
        nested.resolve("dashboard-url.txt"), "http://grafana:3000/d/point?from=1&to=2");

    assertThat(runVerifier(bundle).exitCode()).isZero();
  }

  @Test
  void realBundleSyntaxDoesNotLookLikeJwt() throws Exception {
    Path bundle = Files.createDirectory(temporaryDirectory.resolve("safe-syntax"));
    Files.writeString(
        bundle.resolve("manifest.json"),
        "grafana/k6:0.54.0 127.0.0.1 http://grafana:3000/d/x?from=1&to=2&refresh="
            + " sha256:abcdef0123456789");

    assertThat(runVerifier(bundle).exitCode()).isZero();
  }

  @Test
  void invalidJwtCandidatesPass() throws Exception {
    Path bundle = Files.createDirectory(temporaryDirectory.resolve("invalid-jwt"));
    Files.writeString(
        bundle.resolve("summary.json"),
        "short.a.b invalid!.abcdefgh.abcdefgh eyJ4IjoxfQ.abcdefgh.abcdefgh"
            + " [\"alg\"].abcdefgh.abcdefgh x.abcdefgh.abcdefghz");

    assertThat(runVerifier(bundle).exitCode()).isZero();
  }

  @Test
  void emptyCredentialLabelsPass() throws Exception {
    Path bundle = Files.createDirectory(temporaryDirectory.resolve("empty-credentials"));
    Files.writeString(
        bundle.resolve("k6.log"),
        "Authorization\nAuthorization: \nBearer\nBearer \naccess token prose");

    assertThat(runVerifier(bundle).exitCode()).isZero();
  }

  @Test
  void validatedJwtFailsWithoutDisclosure() throws Exception {
    Path bundle = Files.createDirectory(temporaryDirectory.resolve("jwt"));
    String jwt = validJwt();
    Files.writeString(bundle.resolve("summary.json"), "value=" + jwt);

    VerificationResult result = runVerifier(bundle);

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.output()).doesNotContain(jwt).doesNotContain(jwt.substring(0, 8));
  }

  @Test
  void nonEmptyAuthorizationAndBearerFailWithoutDisclosure() throws Exception {
    Path bundle = Files.createDirectory(temporaryDirectory.resolve("credentials"));
    String authorizationSecret = "synthetic-authorization-secret";
    String bearerSecret = "synthetic-bearer-secret";
    Files.writeString(
        bundle.resolve("k6.log"),
        "Authorization: " + authorizationSecret + "\nBearer " + bearerSecret);

    VerificationResult result = runVerifier(bundle);

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.output()).doesNotContain(authorizationSecret).doesNotContain(bearerSecret);
  }

  @Test
  void accessTokenKeysFailEvenWhenEmptyOrNull() throws Exception {
    for (String key : new String[] {"accessToken", "access_token"}) {
      for (String value : new String[] {"\"synthetic-value\"", "\"\"", "null"}) {
        Path bundle = Files.createTempDirectory(temporaryDirectory, key);
        Files.writeString(bundle.resolve("summary.json"), "{\"" + key + "\":" + value + "}");

        assertThat(runVerifier(bundle).exitCode()).as(key + "=" + value).isNotZero();
      }
    }
  }

  @Test
  void memberIdentityAndSetupDataFail() throws Exception {
    String uuid = "123e4567-e89b-12d3-a456-426614174000";
    String[] values = {
      "{\"memberUuid\":\"synthetic-member\"}",
      "{\"memberUuid\":\"\"}",
      "{\"memberUuid\":null}",
      "{\"member_uuid\":\"synthetic-member\"}",
      "{\"member_uuid\":\"\"}",
      "{\"member_uuid\":null}",
      uuid,
      "{\"setup_data\":{}}",
      "malformed {\"setup_data\":",
      "setup_data=present"
    };
    for (int index = 0; index < values.length; index++) {
      Path bundle = Files.createDirectory(temporaryDirectory.resolve("identity-" + index));
      Files.writeString(bundle.resolve("summary.json"), values[index]);

      VerificationResult result = runVerifier(bundle);

      assertThat(result.exitCode()).isNotZero();
      assertThat(result.output()).doesNotContain(uuid);
    }
  }

  @Test
  void setupDataKeyFailsInNestedJsonLogAndMalformedText() throws Exception {
    Path bundle = Files.createDirectory(temporaryDirectory.resolve("setup-data-contexts"));
    Path nested = Files.createDirectories(bundle.resolve("nested"));
    Files.writeString(nested.resolve("summary.json"), "{\"nested\":{\"setup_data\":null}}");
    Files.writeString(nested.resolve("k6.log"), "setup_data=");
    Files.writeString(nested.resolve("partial.txt"), "malformed {\"setup_data\":");

    VerificationResult result = runVerifier(bundle);

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.output())
        .contains("nested/summary.json: setup_data")
        .contains("nested/k6.log: setup_data")
        .contains("nested/partial.txt: setup_data");
  }

  @Test
  void nestedViolationsReportOnlyRelativePathAndDetector() throws Exception {
    Path bundle = Files.createDirectory(temporaryDirectory.resolve("nested-violations"));
    Path nested = Files.createDirectories(bundle.resolve("a/b"));
    String secret = "synthetic-nested-secret";
    Files.writeString(nested.resolve("artifact.log"), "Bearer " + secret);

    VerificationResult result = runVerifier(bundle);

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.output())
        .contains("a/b/artifact.log")
        .doesNotContain(secret)
        .doesNotContain("Bearer " + secret);
  }

  @Test
  void traversalErrorsFailClosedWithoutDisclosingPathsOutsideTheBundle() throws Exception {
    Path bundle = Files.createDirectory(temporaryDirectory.resolve("walk-error"));
    Path nested = Files.createDirectories(bundle.resolve("nested"));

    VerificationResult result = runVerifierWithWalkError(bundle, nested);

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.output())
        .contains("nested: unreadable-directory")
        .doesNotContain(nested.toString())
        .containsOnlyOnce("nested: unreadable-directory");
  }

  @Test
  void verifierFailsClosedForMissingUnsupportedBinaryAndSymlinkInputs() throws Exception {
    assertThat(runVerifier(temporaryDirectory.resolve("missing")).exitCode()).isNotZero();

    Path file = temporaryDirectory.resolve("not-a-directory.json");
    Files.writeString(file, "{}");
    assertThat(runVerifier(file).exitCode()).isNotZero();

    Path pngBundle = Files.createDirectory(temporaryDirectory.resolve("png"));
    Files.write(pngBundle.resolve("image.png"), new byte[] {1, 2, 3});
    assertThat(runVerifier(pngBundle).exitCode()).isNotZero();

    Path extensionlessBundle = Files.createDirectory(temporaryDirectory.resolve("extensionless"));
    Files.writeString(extensionlessBundle.resolve("artifact"), "safe but unsupported");
    assertThat(runVerifier(extensionlessBundle).exitCode()).isNotZero();

    Path binaryBundle = Files.createDirectory(temporaryDirectory.resolve("binary"));
    Files.write(binaryBundle.resolve("summary.json"), new byte[] {(byte) 0xC3, (byte) 0x28});
    assertThat(runVerifier(binaryBundle).exitCode()).isNotZero();

    Path utf8BinaryBundle = Files.createDirectory(temporaryDirectory.resolve("utf8-binary"));
    Files.write(utf8BinaryBundle.resolve("summary.json"), new byte[] {'{', 0, '}'});
    assertThat(runVerifier(utf8BinaryBundle).exitCode()).isNotZero();

    Path linkBundle = Files.createDirectory(temporaryDirectory.resolve("link"));
    Path target = temporaryDirectory.resolve("target.log");
    Files.writeString(target, "safe");
    Files.createSymbolicLink(linkBundle.resolve("linked.log"), target);
    assertThat(runVerifier(linkBundle).exitCode()).isNotZero();
  }

  private VerificationResult runVerifier(Path bundle) throws Exception {
    Process process =
        new ProcessBuilder(
                "python3", "scripts/verify-load-test-artifact-safety.py", bundle.toString())
            .redirectErrorStream(true)
            .start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return new VerificationResult(process.waitFor(), output);
  }

  private VerificationResult runVerifierWithWalkError(Path bundle, Path unreadableDirectory)
      throws Exception {
    String code =
        "import errno, importlib.util, os, sys; spec ="
            + " importlib.util.spec_from_file_location('verifier',"
            + " 'scripts/verify-load-test-artifact-safety.py'); module ="
            + " importlib.util.module_from_spec(spec); spec.loader.exec_module(module);"
            + " module.os.walk = lambda root, **kwargs: (kwargs['onerror'](OSError(errno.EACCES,"
            + " 'denied', sys.argv[2])), kwargs['onerror'](OSError(errno.EACCES, 'denied',"
            + " sys.argv[2])), iter(()))[2]; sys.exit(module.inspect(module.Path(sys.argv[1])))";
    Process process =
        new ProcessBuilder("python3", "-c", code, bundle.toString(), unreadableDirectory.toString())
            .redirectErrorStream(true)
            .start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return new VerificationResult(process.waitFor(), output);
  }

  private String validJwt() {
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    return encoder.encodeToString(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8))
        + "."
        + encoder.encodeToString("{\"sub\":\"synthetic-subject\"}".getBytes(StandardCharsets.UTF_8))
        + "."
        + encoder.encodeToString("synthetic-signature".getBytes(StandardCharsets.UTF_8));
  }

  private record VerificationResult(int exitCode, String output) {}
}
