package com.oit.dondok.domain.mission.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.oit.dondok.domain.mission.repository.CrewRef;
import com.oit.dondok.domain.mission.repository.MissionLogQueryRepository;
import com.oit.dondok.domain.settlement.service.SettlementNotificationService;
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
  @Mock private SettlementNotificationService settlementNotificationService;

  private MissionAutoCertificationService service;

  @BeforeEach
  void setUp() {
    service =
        new MissionAutoCertificationService(
            missionLogQueryRepository,
            missionAutoCertificationProcessor,
            settlementNotificationService);
  }

  // 처리된 로그가 있으면 영향받은 크루 조회 후 예상 환급금 변동 알림을 발송한다.
  @Test
  void notifiesAffectedCrewsWhenSomeLogsAreProcessed() {
    CrewRef crew1 = new CrewRef(10L, "아침 크루");
    CrewRef crew2 = new CrewRef(20L, "저녁 크루");

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
    given(missionLogQueryRepository.findDistinctCrewsByMissionLogIds(List.of(1L, 3L)))
        .willReturn(List.of(crew1, crew2));

    service.confirmDuePendingReviews();

    verify(missionLogQueryRepository).findDistinctCrewsByMissionLogIds(List.of(1L, 3L));
    verify(settlementNotificationService).sendExpectedRefundChangedNotifications(10L, "아침 크루");
    verify(settlementNotificationService).sendExpectedRefundChangedNotifications(20L, "저녁 크루");
  }

  // 처리된 로그가 없으면 크루 조회와 알림 발송을 모두 건너뛴다.
  @Test
  void skipsNotificationQueryWhenNothingIsProcessed() {
    given(
            missionLogQueryRepository.findAutoCertificationCandidateIds(
                any(LocalDateTime.class), anyInt()))
        .willReturn(List.of(1L, 2L));
    given(missionAutoCertificationProcessor.confirmOne(any(Long.class), any(LocalDateTime.class)))
        .willReturn(false);

    service.confirmDuePendingReviews();

    verify(missionLogQueryRepository, never()).findDistinctCrewsByMissionLogIds(any());
    verify(settlementNotificationService, never())
        .sendExpectedRefundChangedNotifications(any(), any());
  }
}
