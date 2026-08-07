package com.oit.dondok.infra.loadtest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3Client;

class LoadTestFixtureResetServiceTest {
  private static final String FIXTURE_EMAIL_PATTERN = "load-test%@local.invalid";
  private static final String FIXTURE_CREW_TITLE_PATTERN = "load-test-settlement-%";

  private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
  private final LoadTestFixtureResetService service =
      new LoadTestFixtureResetService(
          jdbcTemplate, mock(), mock(S3Client.class), mock(TransactionTemplate.class));

  @Test
  void databaseCleanupBindsTheMatchingFixtureNamespaceForEveryStatement() {
    ReflectionTestUtils.invokeMethod(service, "deleteFixtureDatabaseRows");

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
    verify(jdbcTemplate, times(12)).update(sql.capture(), parameters.capture());

    List<String> statements = sql.getAllValues();
    List<Object[]> boundParameters = parameters.getAllValues();
    assertThat(boundParameters).allSatisfy(parameter -> assertThat(parameter).hasSize(1));
    for (int index = 0; index < statements.size(); index++) {
      String expectedPattern =
          statements.get(index).contains("email like ?")
              ? FIXTURE_EMAIL_PATTERN
              : FIXTURE_CREW_TITLE_PATTERN;
      assertThat(boundParameters.get(index)).containsExactly(expectedPattern);
    }
  }
}
