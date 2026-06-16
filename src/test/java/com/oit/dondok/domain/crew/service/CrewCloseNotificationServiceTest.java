package com.oit.dondok.domain.crew.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import com.oit.dondok.domain.member.entity.Member;
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
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CrewCloseNotificationServiceTest {

  @Mock private CrewRepository crewRepository;
  @Mock private CrewParticipantRepository crewParticipantRepository;
  @Mock private NotificationSender notificationSender;

  @InjectMocks private CrewCloseNotificationService crewCloseNotificationService;

  // D-3 ACTIVE 크루의 LOCKED 참여자 전체에게 종료 예정 알림을 발송한다.
  @Test
  void sendsCloseReminderToLockedParticipants() {
    Crew crew = mock(Crew.class);
    CrewParticipant participant = mock(CrewParticipant.class);
    Member member = mock(Member.class);
    given(crew.getId()).willReturn(1L);
    given(crew.getTitle()).willReturn("아침 갓생 30일");
    given(participant.getMember()).willReturn(member);
    given(participant.getId()).willReturn(10L);

    given(
            crewRepository.findByStatusAndEndAtBetween(
                eq(CrewStatus.ACTIVE), any(LocalDateTime.class), any(LocalDateTime.class)))
        .willReturn(List.of(crew));
    given(
            crewParticipantRepository.findByCrewIdAndStatusAndIdGreaterThanOrderByIdAsc(
                eq(1L), eq(CrewParticipantStatus.LOCKED), eq(0L), any(Pageable.class)))
        .willReturn(List.of(participant));

    crewCloseNotificationService.sendCloseReminders();

    then(notificationSender).should().send(eq(member), any(NotificationPayload.class));
  }

  // 대상 크루가 없으면 알림을 발송하지 않는다.
  @Test
  void doesNotSendWhenNoTargetCrews() {
    given(
            crewRepository.findByStatusAndEndAtBetween(
                any(CrewStatus.class), any(LocalDateTime.class), any(LocalDateTime.class)))
        .willReturn(List.of());

    crewCloseNotificationService.sendCloseReminders();

    then(notificationSender).shouldHaveNoInteractions();
  }

  // 대상 크루는 있으나 LOCKED 참여자가 없으면 알림을 발송하지 않는다.
  @Test
  void doesNotSendWhenNoLockedParticipants() {
    Crew crew = mock(Crew.class);
    given(crew.getId()).willReturn(1L);
    given(
            crewRepository.findByStatusAndEndAtBetween(
                any(CrewStatus.class), any(LocalDateTime.class), any(LocalDateTime.class)))
        .willReturn(List.of(crew));
    given(
            crewParticipantRepository.findByCrewIdAndStatusAndIdGreaterThanOrderByIdAsc(
                anyLong(), any(CrewParticipantStatus.class), anyLong(), any(Pageable.class)))
        .willReturn(List.of());

    crewCloseNotificationService.sendCloseReminders();

    then(notificationSender).shouldHaveNoInteractions();
  }

  // 개별 알림 발송 실패 시 예외를 삼키고 나머지 참여자에게 계속 발송한다.
  @Test
  void continuesWhenParticipantNotificationFails() {
    Crew crew = mock(Crew.class);
    CrewParticipant participant = mock(CrewParticipant.class);
    Member member = mock(Member.class);
    given(crew.getId()).willReturn(1L);
    given(crew.getTitle()).willReturn("크루");
    given(participant.getMember()).willReturn(member);
    given(participant.getId()).willReturn(5L);

    given(
            crewRepository.findByStatusAndEndAtBetween(
                any(CrewStatus.class), any(LocalDateTime.class), any(LocalDateTime.class)))
        .willReturn(List.of(crew));
    given(
            crewParticipantRepository.findByCrewIdAndStatusAndIdGreaterThanOrderByIdAsc(
                anyLong(), any(CrewParticipantStatus.class), anyLong(), any(Pageable.class)))
        .willReturn(List.of(participant));
    willThrow(new RuntimeException("FCM 실패")).given(notificationSender).send(any(), any());

    assertThatCode(() -> crewCloseNotificationService.sendCloseReminders())
        .doesNotThrowAnyException();
  }

  // 첫 배치가 가득 차면 cursor를 전진시켜 다음 페이지를 조회한다.
  @Test
  void cursorAdvancesToNextBatchWhenBatchFull() {
    Crew crew = mock(Crew.class);
    CrewParticipant participant = mock(CrewParticipant.class);
    Member member = mock(Member.class);
    given(crew.getId()).willReturn(1L);
    given(crew.getTitle()).willReturn("크루");
    given(participant.getMember()).willReturn(member);
    given(participant.getId()).willReturn(500L);

    List<CrewParticipant> fullBatch = Collections.nCopies(500, participant);

    given(
            crewRepository.findByStatusAndEndAtBetween(
                any(CrewStatus.class), any(LocalDateTime.class), any(LocalDateTime.class)))
        .willReturn(List.of(crew));
    given(
            crewParticipantRepository.findByCrewIdAndStatusAndIdGreaterThanOrderByIdAsc(
                eq(1L), eq(CrewParticipantStatus.LOCKED), eq(0L), any(Pageable.class)))
        .willReturn(fullBatch);
    given(
            crewParticipantRepository.findByCrewIdAndStatusAndIdGreaterThanOrderByIdAsc(
                eq(1L), eq(CrewParticipantStatus.LOCKED), eq(500L), any(Pageable.class)))
        .willReturn(List.of());

    crewCloseNotificationService.sendCloseReminders();

    then(crewParticipantRepository)
        .should(org.mockito.BDDMockito.times(2))
        .findByCrewIdAndStatusAndIdGreaterThanOrderByIdAsc(
            eq(1L), eq(CrewParticipantStatus.LOCKED), anyLong(), any(Pageable.class));
  }
}
