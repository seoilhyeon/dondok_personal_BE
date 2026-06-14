package com.oit.dondok.domain.mission.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
                anyInt()))
        .willReturn(List.of(participant));

    missionDeadlineNotificationService.sendDeadlineReminders(DailySettlementType.A);

    then(notificationSender).should().send(eq(member), any(NotificationPayload.class));
  }

  // 대상 참여자가 없으면 알림을 발송하지 않는다.
  @Test
  void doesNotSendWhenNoTargets() {
    given(
            missionLogQueryRepository.findDeadlineReminderTargets(
                eq(DailySettlementType.B),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                anyInt()))
        .willReturn(List.of());

    missionDeadlineNotificationService.sendDeadlineReminders(DailySettlementType.B);

    then(notificationSender).shouldHaveNoInteractions();
  }
}
