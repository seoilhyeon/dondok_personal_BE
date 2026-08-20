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
  private static final String POINT_POOL_EMAIL_PATTERN = "load-test+point-%@local.invalid";
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
    verify(jdbcTemplate, times(13)).update(sql.capture(), parameters.capture());

    List<String> statements = sql.getAllValues();
    List<Object[]> boundParameters = parameters.getAllValues();
    for (int index = 0; index < statements.size(); index++) {
      Object[] parametersForStatement = boundParameters.get(index);
      if (statements.get(index).contains("email not like ?")) {
        assertThat(parametersForStatement)
            .containsExactly(FIXTURE_EMAIL_PATTERN, POINT_POOL_EMAIL_PATTERN);
      } else if (statements.get(index).startsWith("update point_account")) {
        assertThat(parametersForStatement).containsExactly(POINT_POOL_EMAIL_PATTERN);
      } else {
        String expectedPattern =
            statements.get(index).contains("email like ?")
                ? FIXTURE_EMAIL_PATTERN
                : FIXTURE_CREW_TITLE_PATTERN;
        assertThat(parametersForStatement).containsExactly(expectedPattern);
      }
    }
  }

  @Test
  void databaseCleanupPreservesAndZerosTheStablePointAccountPool() {
    ReflectionTestUtils.invokeMethod(service, "deleteFixtureDatabaseRows");

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
    verify(jdbcTemplate, times(13)).update(sql.capture(), parameters.capture());

    assertThat(sql.getAllValues())
        .anySatisfy(
            statement ->
                assertThat(statement)
                    .contains("update point_account")
                    .contains("available_balance = 0")
                    .contains("email like ?"))
        .anySatisfy(
            statement ->
                assertThat(statement)
                    .contains("delete from point_account")
                    .contains("email not like ?"))
        .anySatisfy(
            statement ->
                assertThat(statement).contains("delete from member").contains("email not like ?"));
    assertThat(parameters.getAllValues())
        .anySatisfy(values -> assertThat(values).contains(POINT_POOL_EMAIL_PATTERN));
  }
}
