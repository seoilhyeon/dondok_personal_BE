package com.oit.dondok.domain.settlement.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DailySettlementSnapshotTest {

  @Test
  void provisionalThrowsInvalidInputWhenAggregateValueIsNegative() {
    Crew crew = org.mockito.Mockito.mock(Crew.class);

    assertThatThrownBy(
            () ->
                DailySettlementSnapshot.provisional(
                    crew,
                    LocalDate.of(2026, 6, 15),
                    DailySettlementType.A,
                    MissionFrequencyType.DAILY,
                    "batch-key",
                    LocalDateTime.of(2026, 6, 15, 12, 0),
                    -1,
                    0,
                    0L))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .satisfies(errorCode -> assertThat(errorCode).isEqualTo(GlobalErrorCode.INVALID_INPUT));
  }

  @Test
  void finalizedFailedStoresFailureStateAndTruncatesFailureMessage() {
    Crew crew = org.mockito.Mockito.mock(Crew.class);
    String longMessage = "가".repeat(501);

    DailySettlementSnapshot snapshot =
        DailySettlementSnapshot.finalizedFailed(
            crew,
            LocalDate.of(2026, 6, 15),
            DailySettlementType.A,
            MissionFrequencyType.DAILY,
            "batch-key",
            LocalDateTime.of(2026, 6, 15, 12, 0),
            longMessage);

    assertThat(snapshot.getPhase()).isEqualTo(DailySettlementPhase.FINALIZED);
    assertThat(snapshot.getStatus()).isEqualTo(DailySettlementStatus.FAILED);
    assertThat(snapshot.getFailureMessage()).hasSize(500);
    assertThat(snapshot.getTotalParticipants()).isZero();
  }

  @Test
  void markSucceededClearsFailureMessage() {
    Crew crew = org.mockito.Mockito.mock(Crew.class);
    DailySettlementSnapshot snapshot =
        DailySettlementSnapshot.finalizedFailed(
            crew,
            LocalDate.of(2026, 6, 15),
            DailySettlementType.A,
            MissionFrequencyType.DAILY,
            "failed-batch-key",
            LocalDateTime.of(2026, 6, 15, 12, 0),
            "실패");

    snapshot.markSucceeded("retry-batch-key", LocalDateTime.of(2026, 6, 16, 12, 0), 2, 1, 2_000L);

    assertThat(snapshot.getStatus()).isEqualTo(DailySettlementStatus.SUCCEEDED);
    assertThat(snapshot.getFailureMessage()).isNull();
    assertThat(snapshot.getBatchRunKey()).isEqualTo("retry-batch-key");
    assertThat(snapshot.getTotalParticipants()).isEqualTo(2);
  }

  @Test
  void finalizedFailedStartsRetryCountAtOneAndStopsAfterMaxRetryCount() {
    Crew crew = org.mockito.Mockito.mock(Crew.class);

    DailySettlementSnapshot snapshot =
        DailySettlementSnapshot.finalizedFailed(
            crew,
            LocalDate.of(2026, 6, 15),
            DailySettlementType.A,
            MissionFrequencyType.DAILY,
            "batch-key",
            LocalDateTime.of(2026, 6, 15, 12, 0),
            "실패");

    assertThat(snapshot.getRetryCount()).isEqualTo(1);
    assertThat(snapshot.canRetry()).isTrue();

    snapshot.markFailed("retry-2", LocalDateTime.of(2026, 6, 16, 12, 0), "2차 실패");
    snapshot.markFailed("retry-3", LocalDateTime.of(2026, 6, 17, 12, 0), "3차 실패");

    assertThat(snapshot.getRetryCount()).isEqualTo(DailySettlementSnapshot.MAX_RETRY_COUNT);
    assertThat(snapshot.canRetry()).isFalse();
  }

  @Test
  void succeededSnapshotStartsRetryCountAtZero() {
    Crew crew = org.mockito.Mockito.mock(Crew.class);

    DailySettlementSnapshot snapshot =
        DailySettlementSnapshot.finalized(
            crew,
            LocalDate.of(2026, 6, 15),
            DailySettlementType.A,
            MissionFrequencyType.DAILY,
            "batch-key",
            LocalDateTime.of(2026, 6, 15, 12, 0),
            1,
            1,
            1_000L);

    assertThat(snapshot.getRetryCount()).isZero();
  }
}
