package com.oit.dondok.domain.mission.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.oit.dondok.domain.mission.repository.MissionLogQueryRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MissionAutoCertificationServiceTest {

  @Mock private MissionLogQueryRepository missionLogQueryRepository;
  @Mock private MissionAutoCertificationProcessor missionAutoCertificationProcessor;

  private MissionAutoCertificationService service;

  @BeforeEach
  void setUp() {
    service =
        new MissionAutoCertificationService(
            missionLogQueryRepository, missionAutoCertificationProcessor);
  }

  @Test
  void confirmsProcessableLogs() {
    given(
            missionLogQueryRepository.findAutoCertificationCandidateIds(
                any(LocalDateTime.class), anyInt()))
        .willReturn(List.of(1L, 2L, 3L));
    given(missionAutoCertificationProcessor.confirmOne(eq(1L), any(LocalDateTime.class)))
        .willReturn(true);
    given(missionAutoCertificationProcessor.confirmOne(eq(2L), any(LocalDateTime.class)))
        .willReturn(false);
    given(missionAutoCertificationProcessor.confirmOne(eq(3L), any(LocalDateTime.class)))
        .willReturn(true);

    service.confirmDuePendingReviews();

    verify(missionAutoCertificationProcessor).confirmOne(eq(1L), any(LocalDateTime.class));
    verify(missionAutoCertificationProcessor).confirmOne(eq(2L), any(LocalDateTime.class));
    verify(missionAutoCertificationProcessor).confirmOne(eq(3L), any(LocalDateTime.class));
  }

  @Test
  void skipsProcessingWhenNoCandidates() {
    given(
            missionLogQueryRepository.findAutoCertificationCandidateIds(
                any(LocalDateTime.class), anyInt()))
        .willReturn(List.of());

    service.confirmDuePendingReviews();

    verify(missionAutoCertificationProcessor, never())
        .confirmOne(any(Long.class), any(LocalDateTime.class));
  }
}
