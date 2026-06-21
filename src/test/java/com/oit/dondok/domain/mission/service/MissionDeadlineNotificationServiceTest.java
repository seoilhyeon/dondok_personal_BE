package com.oit.dondok.domain.mission.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.repository.MissionLogQueryRepository;
import com.oit.dondok.domain.notification.port.NotificationPayload;
import com.oit.dondok.domain.notification.port.NotificationSender;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MissionDeadlineNotificationServiceTest {

  @Mock private MissionLogQueryRepository missionLogQueryRepository;
  @Mock private NotificationSender notificationSender;

  @InjectMocks private MissionDeadlineNotificationService missionDeadlineNotificationService;

  // 오늘 인증하지 않은 LOCKED 참여자에게 마감 임박 알림을 발송한다.
  @Test
  void sendsDeadlineReminderToTargetParticipants() {
    CrewParticipant participant = mock(CrewParticipant.class);
    Crew crew = mock(Crew.class);
    Member member = mock(Member.class);
    given(participant.getCrew()).willReturn(crew);
    given(participant.getMember()).willReturn(member);
    given(crew.getId()).willReturn(1L);
    given(crew.getTitle()).willReturn("morning crew");
    given(
            missionLogQueryRepository.findDeadlineReminderTargets(
                eq(DailySettlementType.A),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                anyInt(),
                anyLong(),
                anyInt()))
        .willReturn(List.of(participant));

    missionDeadlineNotificationService.sendDeadlineReminders(DailySettlementType.A);

    then(notificationSender).should().send(eq(member), any(NotificationPayload.class));
  }

  // 첫 배치가 가득 차면 cursor를 전진시켜 다음 페이지를 조회한다.
  @Test
  void cursorAdvancesToNextBatchWhenBatchFull() {
    CrewParticipant participant = mock(CrewParticipant.class);
    Crew crew = mock(Crew.class);
    Member member = mock(Member.class);
    given(participant.getCrew()).willReturn(crew);
    given(participant.getMember()).willReturn(member);
    given(crew.getId()).willReturn(1L);
    given(crew.getTitle()).willReturn("crew");
    given(participant.getId()).willReturn(500L);

    List<CrewParticipant> fullBatch = Collections.nCopies(500, participant);

    given(
            missionLogQueryRepository.findDeadlineReminderTargets(
                eq(DailySettlementType.C),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                anyInt(),
                eq(0L),
                anyInt()))
        .willReturn(fullBatch);
    given(
            missionLogQueryRepository.findDeadlineReminderTargets(
                eq(DailySettlementType.C),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                anyInt(),
                eq(500L),
                anyInt()))
        .willReturn(List.of());

    missionDeadlineNotificationService.sendDeadlineReminders(DailySettlementType.C);

    then(missionLogQueryRepository)
        .should(org.mockito.BDDMockito.times(2))
        .findDeadlineReminderTargets(
            eq(DailySettlementType.C),
            any(LocalDateTime.class),
            any(LocalDateTime.class),
            anyInt(),
            anyLong(),
            anyInt());
  }

  // 대상 참여자가 없으면 알림을 발송하지 않는다.
  @Test
  void doesNotSendWhenNoTargets() {
    given(
            missionLogQueryRepository.findDeadlineReminderTargets(
                eq(DailySettlementType.B),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                anyInt(),
                anyLong(),
                anyInt()))
        .willReturn(List.of());

    missionDeadlineNotificationService.sendDeadlineReminders(DailySettlementType.B);

    then(notificationSender).shouldHaveNoInteractions();
  }
}
