package com.oit.dondok.infra.loadtest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3Client;

class LoadTestFixtureResetServiceTest {
  private static final String FIXTURE_EMAIL_PATTERN = "load-test%@local.invalid";
  private static final String POINT_POOL_EMAIL_PATTERN = "load-test+point-pool-%@local.invalid";
  private static final String FIXTURE_CREW_TITLE_PATTERN = "load-test-settlement-%";

  private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
  private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
  private final LoadTestFixtureResetService service =
      new LoadTestFixtureResetService(
          jdbcTemplate, mock(), mock(S3Client.class), transactionTemplate);

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

  @Test
  void failedSettlementRunCleanupTargetsOnlyThatRunNamespace() {
    doAnswer(
            invocation -> {
              invocation
                  .<java.util.function.Consumer<TransactionStatus>>getArgument(0)
                  .accept(mock(TransactionStatus.class));
              return null;
            })
        .when(transactionTemplate)
        .executeWithoutResult(org.mockito.ArgumentMatchers.any());

    service.deleteFinalSettlementRun("failed-run");

    ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
    verify(jdbcTemplate, times(12))
        .update(org.mockito.ArgumentMatchers.anyString(), parameters.capture());
    String emailPattern =
        "^load-test[+]failed-run-[0-9]+-settlement-(host|member)@local[.]invalid$";
    String crewTitlePattern = "^load-test-settlement-failed-run-[0-9]+$";
    assertThat(parameters.getAllValues())
        .allSatisfy(
            values ->
                assertThat(values)
                    .allMatch(
                        value -> value.equals(emailPattern) || value.equals(crewTitlePattern)));
    assertThat(
            Pattern.matches(
                emailPattern, "load-test+failed-run-extra-0-settlement-host@local.invalid"))
        .isFalse();
    assertThat(Pattern.matches(crewTitlePattern, "load-test-settlement-failed-run-extra-0"))
        .isFalse();
  }
}
