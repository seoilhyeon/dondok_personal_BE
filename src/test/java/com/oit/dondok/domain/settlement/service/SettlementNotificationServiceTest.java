package com.oit.dondok.domain.settlement.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

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
  void sendSettlementCompletedNotificationsSendsFcmToAllItems() {
    Member member1 = mock(Member.class);
    Member member2 = mock(Member.class);
    SettlementItem item1 = mock(SettlementItem.class);
    SettlementItem item2 = mock(SettlementItem.class);
    given(item1.getMember()).willReturn(member1);
    given(item2.getMember()).willReturn(member2);
    Settlement settlement = mock(Settlement.class);
    Crew crew = mock(Crew.class);
    given(settlement.getId()).willReturn(SETTLEMENT_ID);
    given(settlement.getCrew()).willReturn(crew);
    given(crew.getTitle()).willReturn("morning crew");

    settlementNotificationService.sendSettlementCompletedNotifications(
        settlement, List.of(item1, item2));

    then(notificationSender).should().send(eq(member1), any(NotificationPayload.class));
    then(notificationSender).should().send(eq(member2), any(NotificationPayload.class));
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
  void sendSettlementCompletedNotificationsSendsEmailToAllItems() {
    Member member1 = mock(Member.class);
    Member member2 = mock(Member.class);
    given(member1.getEmail()).willReturn("m1@example.com");
    given(member1.getNickname()).willReturn("닉네임1");
    given(member2.getEmail()).willReturn("m2@example.com");
    given(member2.getNickname()).willReturn("닉네임2");
    SettlementItem item1 = mock(SettlementItem.class);
    SettlementItem item2 = mock(SettlementItem.class);
    given(item1.getMember()).willReturn(member1);
    given(item1.getRefundAmount()).willReturn(10000L);
    given(item2.getMember()).willReturn(member2);
    given(item2.getRefundAmount()).willReturn(20000L);
    Settlement settlement = mock(Settlement.class);
    Crew crew = mock(Crew.class);
    given(settlement.getId()).willReturn(SETTLEMENT_ID);
    given(settlement.getCrew()).willReturn(crew);
    given(crew.getTitle()).willReturn("morning crew");

    settlementNotificationService.sendSettlementCompletedNotifications(
        settlement, List.of(item1, item2));

    then(emailSender).should().send(eq("m1@example.com"), any(), any());
    then(emailSender).should().send(eq("m2@example.com"), any(), any());
  }

  @Test
  void sendSettlementCompletedNotificationsSkipsEmailWhenAddressBlank() {
    Member member = mock(Member.class);
    given(member.getEmail()).willReturn("  ");
    SettlementItem item = mock(SettlementItem.class);
    given(item.getMember()).willReturn(member);
    Settlement settlement = mock(Settlement.class);
    Crew crew = mock(Crew.class);
    given(settlement.getId()).willReturn(SETTLEMENT_ID);
    given(settlement.getCrew()).willReturn(crew);
    given(crew.getTitle()).willReturn("morning crew");

    settlementNotificationService.sendSettlementCompletedNotifications(settlement, List.of(item));

    then(emailSender).should(never()).send(any(), any(), any());
  }

  @Test
  void sendSettlementCompletedNotificationsSendsNothingWhenItemsEmpty() {
    Settlement settlement = mock(Settlement.class);
    Crew crew = mock(Crew.class);
    given(settlement.getId()).willReturn(SETTLEMENT_ID);
    given(settlement.getCrew()).willReturn(crew);
    given(crew.getTitle()).willReturn("morning crew");

    settlementNotificationService.sendSettlementCompletedNotifications(settlement, List.of());

    then(notificationSender).shouldHaveNoInteractions();
    then(emailSender).shouldHaveNoInteractions();
  }
}
