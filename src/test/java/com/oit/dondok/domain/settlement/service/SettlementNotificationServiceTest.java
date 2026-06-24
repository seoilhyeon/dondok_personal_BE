package com.oit.dondok.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.domain.notification.port.EmailSender;
import com.oit.dondok.domain.notification.port.NotificationPayload;
import com.oit.dondok.domain.notification.port.NotificationSender;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementItem;
import com.oit.dondok.domain.settlement.entity.SettlementStatus;
import com.oit.dondok.domain.settlement.repository.SettlementItemRepository;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementNotificationServiceTest {

  private static final Long CREW_ID = 10L;
  private static final Long SETTLEMENT_ID = 501L;

  @Mock private CrewParticipantRepository crewParticipantRepository;
  @Mock private MemberRepository memberRepository;
  @Mock private SettlementRepository settlementRepository;
  @Mock private SettlementItemRepository settlementItemRepository;
  @Mock private NotificationSender notificationSender;
  @Mock private EmailSender emailSender;

  @InjectMocks private SettlementNotificationService settlementNotificationService;

  @Test
  void onSettlementRefundCreditedLoadsMemberInNotificationTransaction() {
    Member member = mock(Member.class);
    given(memberRepository.findById(100L)).willReturn(Optional.of(member));

    settlementNotificationService.onSettlementRefundCredited(
        new SettlementRefundCreditedNotificationEvent(
            100L, SETTLEMENT_ID, CREW_ID, 7_000L, "morning crew"));

    then(notificationSender).should().send(eq(member), any(NotificationPayload.class));
  }

  @Test
  void onSettlementRefundCreditedSkipsWhenMemberMissing() {
    given(memberRepository.findById(100L)).willReturn(Optional.empty());

    settlementNotificationService.onSettlementRefundCredited(
        new SettlementRefundCreditedNotificationEvent(
            100L, SETTLEMENT_ID, CREW_ID, 7_000L, "morning crew"));

    then(notificationSender).shouldHaveNoInteractions();
  }

  @Test
  void sendExpectedRefundChangedNotificationsSendsToAllLockedParticipants() {
    Member member1 = mock(Member.class);
    Member member2 = mock(Member.class);
    CrewParticipant participant1 = mock(CrewParticipant.class);
    CrewParticipant participant2 = mock(CrewParticipant.class);
    given(participant1.getMember()).willReturn(member1);
    given(participant2.getMember()).willReturn(member2);
    given(crewParticipantRepository.findByCrewIdAndStatus(CREW_ID, CrewParticipantStatus.LOCKED))
        .willReturn(List.of(participant1, participant2));

    settlementNotificationService.sendExpectedRefundChangedNotifications(CREW_ID, "morning crew");

    then(notificationSender).should().send(eq(member1), any(NotificationPayload.class));
    then(notificationSender).should().send(eq(member2), any(NotificationPayload.class));
  }

  @Test
  void sendExpectedRefundChangedNotificationsSendsNothingWhenNoLockedParticipants() {
    given(crewParticipantRepository.findByCrewIdAndStatus(CREW_ID, CrewParticipantStatus.LOCKED))
        .willReturn(List.of());

    settlementNotificationService.sendExpectedRefundChangedNotifications(CREW_ID, "morning crew");

    then(notificationSender).shouldHaveNoInteractions();
  }

  @Test
  void onSettlementCompletedLoadsSettlementAndItemsInNotificationTransaction() {
    Member member = mock(Member.class);
    SettlementItem item = mock(SettlementItem.class);
    given(item.getMember()).willReturn(member);
    Settlement settlement = mock(Settlement.class);
    Crew crew = mock(Crew.class);
    given(settlement.getId()).willReturn(SETTLEMENT_ID);
    given(settlement.getCrew()).willReturn(crew);
    given(crew.getTitle()).willReturn("morning crew");
    given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.of(settlement));
    given(settlementItemRepository.findBySettlementIdOrderByIdAsc(SETTLEMENT_ID))
        .willReturn(List.of(item));

    settlementNotificationService.onSettlementCompleted(
        new SettlementCompletedNotificationEvent(SETTLEMENT_ID));

    then(notificationSender).should().send(eq(member), any(NotificationPayload.class));
  }

  @Test
  void onSettlementCompletedSkipsWhenSettlementMissing() {
    given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.empty());

    settlementNotificationService.onSettlementCompleted(
        new SettlementCompletedNotificationEvent(SETTLEMENT_ID));

    then(settlementItemRepository).shouldHaveNoInteractions();
    then(notificationSender).shouldHaveNoInteractions();
    then(emailSender).shouldHaveNoInteractions();
  }

  @Test
  void resendSettlementCompletedNotificationsSendsOnlyForSucceededSettlement() {
    Member member = mock(Member.class);
    SettlementItem item = mock(SettlementItem.class);
    given(item.getMember()).willReturn(member);
    Settlement settlement = mock(Settlement.class);
    Crew crew = mock(Crew.class);
    given(settlement.getStatus()).willReturn(SettlementStatus.SUCCEEDED);
    given(settlement.getId()).willReturn(SETTLEMENT_ID);
    given(settlement.getCrew()).willReturn(crew);
    given(crew.getTitle()).willReturn("morning crew");
    given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.of(settlement));
    given(settlementItemRepository.findBySettlementIdOrderByIdAsc(SETTLEMENT_ID))
        .willReturn(List.of(item));

    int sentItemCount =
        settlementNotificationService.resendSettlementCompletedNotifications(SETTLEMENT_ID);

    assertThat(sentItemCount).isEqualTo(1);
    then(notificationSender).should().send(eq(member), any(NotificationPayload.class));
  }

  @Test
  void resendSettlementCompletedNotificationsRejectsNotSucceededSettlement() {
    Settlement settlement = mock(Settlement.class);
    given(settlement.getStatus()).willReturn(SettlementStatus.RUNNING);
    given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.of(settlement));

    assertThatThrownBy(
            () ->
                settlementNotificationService.resendSettlementCompletedNotifications(SETTLEMENT_ID))
        .isInstanceOf(IllegalStateException.class);

    then(settlementItemRepository).shouldHaveNoInteractions();
    then(notificationSender).shouldHaveNoInteractions();
    then(emailSender).shouldHaveNoInteractions();
  }
}
