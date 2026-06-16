package com.oit.dondok.domain.mission.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.repository.MissionLogQueryRepository;
import com.oit.dondok.domain.notification.port.NotificationPayload;
import com.oit.dondok.domain.notification.port.NotificationSender;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnreviewedMissionNotificationServiceTest {

  @Mock private MissionLogQueryRepository missionLogQueryRepository;
  @Mock private NotificationSender notificationSender;

  @InjectMocks private UnreviewedMissionNotificationService unreviewedMissionNotificationService;

  private static final LocalDate MISSION_DATE = LocalDate.of(2026, 6, 17);

  // 슬롯 내 PENDING_REVIEW 인증이 있는 크루의 방장에게 알림을 발송한다.
  @Test
  void sendsUnreviewedReminderToHost() {
    Crew crew = mock(Crew.class);
    Member host = mock(Member.class);
    given(crew.getId()).willReturn(1L);
    given(crew.getTitle()).willReturn("갓생 30일");
    given(crew.getHostMember()).willReturn(host);

    given(
            missionLogQueryRepository.findUnreviewedHostTargets(
                eq(DailySettlementType.A),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(0L),
                anyInt()))
        .willReturn(List.of(crew));

    unreviewedMissionNotificationService.sendUnreviewedReminders(
        DailySettlementType.A, MISSION_DATE);

    then(notificationSender).should().send(eq(host), any(NotificationPayload.class));
  }

  // 대상 크루가 없으면 알림을 발송하지 않는다.
  @Test
  void doesNotSendWhenNoUnreviewedCrews() {
    given(
            missionLogQueryRepository.findUnreviewedHostTargets(
                any(DailySettlementType.class),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                anyLong(),
                anyInt()))
        .willReturn(List.of());

    unreviewedMissionNotificationService.sendUnreviewedReminders(
        DailySettlementType.A, MISSION_DATE);

    then(notificationSender).shouldHaveNoInteractions();
  }

  // 첫 번째 크루 알림 발송 실패 시 예외를 삼키고 두 번째 크루에 계속 발송한다.
  @Test
  void continuesWhenHostNotificationFails() {
    Crew crew1 = mock(Crew.class);
    Crew crew2 = mock(Crew.class);
    Member host1 = mock(Member.class);
    Member host2 = mock(Member.class);
    given(crew1.getId()).willReturn(1L);
    given(crew1.getTitle()).willReturn("크루1");
    given(crew1.getHostMember()).willReturn(host1);
    given(crew2.getId()).willReturn(2L);
    given(crew2.getTitle()).willReturn("크루2");
    given(crew2.getHostMember()).willReturn(host2);

    given(
            missionLogQueryRepository.findUnreviewedHostTargets(
                any(DailySettlementType.class),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                anyLong(),
                anyInt()))
        .willReturn(List.of(crew1, crew2));
    willThrow(new RuntimeException("FCM 실패")).given(notificationSender).send(eq(host1), any());

    unreviewedMissionNotificationService.sendUnreviewedReminders(
        DailySettlementType.A, MISSION_DATE);

    then(notificationSender).should().send(eq(host2), any(NotificationPayload.class));
  }

  // 첫 배치가 가득 차면 마지막 크루의 id를 cursor로 전진시켜 다음 페이지를 조회한다.
  @Test
  void cursorAdvancesToNextBatchWhenBatchFull() {
    Crew middleCrew = mock(Crew.class);
    Crew lastCrew = mock(Crew.class);
    Member host = mock(Member.class);
    given(middleCrew.getId()).willReturn(1L);
    given(middleCrew.getTitle()).willReturn("크루");
    given(middleCrew.getHostMember()).willReturn(host);
    given(lastCrew.getId()).willReturn(999L);
    given(lastCrew.getTitle()).willReturn("크루");
    given(lastCrew.getHostMember()).willReturn(host);

    List<Crew> fullBatch = new ArrayList<>(Collections.nCopies(499, middleCrew));
    fullBatch.add(lastCrew);

    given(
            missionLogQueryRepository.findUnreviewedHostTargets(
                any(DailySettlementType.class),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(0L),
                anyInt()))
        .willReturn(fullBatch);
    given(
            missionLogQueryRepository.findUnreviewedHostTargets(
                any(DailySettlementType.class),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(999L),
                anyInt()))
        .willReturn(List.of());

    unreviewedMissionNotificationService.sendUnreviewedReminders(
        DailySettlementType.A, MISSION_DATE);

    then(missionLogQueryRepository)
        .should(times(2))
        .findUnreviewedHostTargets(
            any(DailySettlementType.class),
            any(LocalDateTime.class),
            any(LocalDateTime.class),
            anyLong(),
            anyInt());
  }
}
