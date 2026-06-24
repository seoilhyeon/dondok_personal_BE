package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.domain.notification.port.EmailSender;
import com.oit.dondok.domain.notification.port.NotificationPayload;
import com.oit.dondok.domain.notification.port.NotificationSender;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementItem;
import com.oit.dondok.domain.settlement.entity.SettlementStatus;
import com.oit.dondok.domain.settlement.repository.SettlementItemRepository;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import com.oit.dondok.infra.ses.template.SettlementCompletedEmailTemplate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementNotificationService {

  private final CrewParticipantRepository crewParticipantRepository;
  private final MemberRepository memberRepository;
  private final SettlementRepository settlementRepository;
  private final SettlementItemRepository settlementItemRepository;
  private final NotificationSender notificationSender;
  private final EmailSender emailSender;

  @Value("${app.frontend.base-url:https://dondok-fe.vercel.app}")
  private String frontendBaseUrl = "https://dondok-fe.vercel.app";

  // SettlementBatchCommandService.refundOneSettlementItem() 커밋 이후 호출된다.
  // REQUIRES_NEW로 분리해 알림 실패가 정산 트랜잭션에 영향을 주지 않도록 한다.
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onSettlementRefundCredited(SettlementRefundCreditedNotificationEvent event) {
    try {
      var member = memberRepository.findById(event.memberId());
      if (member.isEmpty()) {
        log.error(
            "[알림] 환급 완료 알림 대상 회원 없음 settlementId={}, memberId={}",
            event.settlementId(),
            event.memberId());
        return;
      }
      notificationSender.send(
          member.get(),
          new NotificationPayload(
              "SETTLEMENT_REFUND_CREDITED",
              "settlement",
              String.valueOf(event.settlementId()),
              "dondok://crews/" + event.crewId() + "/settlement",
              event.refundAmount() + "원이 환급되었습니다.",
              event.crewTitle()));
    } catch (RuntimeException e) {
      log.warn("[알림] 환급 완료 알림 발송 실패 settlementId={}", event.settlementId(), e);
    }
  }

  // 미션 인증 검수 결과로 크루 전체 예상 환급금이 변동될 수 있으므로 LOCKED 참여자 전원에게 알린다.
  @Transactional
  public void sendExpectedRefundChangedNotifications(Long crewId, String crewTitle) {
    List<CrewParticipant> participants =
        crewParticipantRepository.findByCrewIdAndStatus(crewId, CrewParticipantStatus.LOCKED);
    for (CrewParticipant participant : participants) {
      try {
        notificationSender.send(
            participant.getMember(),
            new NotificationPayload(
                "SETTLEMENT_EXPECTED_REFUND_CHANGED",
                "crew",
                String.valueOf(crewId),
                "dondok://crews/" + crewId + "/dashboard",
                crewTitle + " 크루 예상 환급금이 변동되었습니다.",
                crewTitle));
      } catch (RuntimeException e) {
        log.warn(
            "[알림] 예상 환급금 변동 알림 발송 실패 crewId={}, participantId={}", crewId, participant.getId(), e);
      }
    }
  }

  // SettlementBatchCommandService.verifyAndMarkSucceeded() 커밋 이후 호출된다.
  // REQUIRES_NEW로 분리해 알림 실패가 정산 트랜잭션에 영향을 주지 않도록 한다.
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onSettlementCompleted(SettlementCompletedNotificationEvent event) {
    var settlement = settlementRepository.findById(event.settlementId());
    if (settlement.isEmpty()) {
      log.error("[알림] 정산 완료 알림 대상 정산 없음 settlementId={}", event.settlementId());
      return;
    }
    List<SettlementItem> items =
        settlementItemRepository.findBySettlementIdOrderByIdAsc(event.settlementId());
    dispatchSettlementCompletedNotifications(settlement.get(), items);
  }

  @Transactional
  public int resendSettlementCompletedNotifications(Long settlementId) {
    Settlement settlement =
        settlementRepository
            .findById(settlementId)
            .orElseThrow(
                () -> new IllegalArgumentException("정산을 찾을 수 없습니다. settlementId=" + settlementId));
    if (settlement.getStatus() != SettlementStatus.SUCCEEDED) {
      throw new IllegalStateException("완료된 정산만 완료 알림을 재발송할 수 있습니다. settlementId=" + settlementId);
    }
    List<SettlementItem> items =
        settlementItemRepository.findBySettlementIdOrderByIdAsc(settlementId);
    dispatchSettlementCompletedNotifications(settlement, items);
    return items.size();
  }

  private void dispatchSettlementCompletedNotifications(
      Settlement settlement, List<SettlementItem> items) {
    Long settlementId = settlement.getId();
    Long crewId = settlement.getCrew().getId();
    String crewTitle = settlement.getCrew().getTitle();
    String fcmDeepLink = "dondok://crews/" + crewId + "/settlement";
    String emailDeepLink = frontendBaseUrl + "/crews/" + crewId + "/settlement";
    for (SettlementItem item : items) {
      try {
        notificationSender.send(
            item.getMember(),
            new NotificationPayload(
                "SETTLEMENT_COMPLETED",
                "settlement",
                String.valueOf(settlementId),
                fcmDeepLink,
                crewTitle + " 크루 정산이 완료되었습니다.",
                crewTitle));
      } catch (RuntimeException e) {
        log.warn(
            "[FCM] 정산 완료 알림 발송 실패 settlementId={}, memberUuid={}",
            settlementId,
            item.getMember().getUuid(),
            e);
      }
      sendSettlementCompletedEmail(item, crewTitle, emailDeepLink);
    }
  }

  private void sendSettlementCompletedEmail(
      SettlementItem item, String crewTitle, String emailDeepLink) {
    try {
      String memberEmail = item.getMember().getEmail();
      if (memberEmail != null && !memberEmail.isBlank()) {
        emailSender.send(
            memberEmail,
            SettlementCompletedEmailTemplate.subject(crewTitle),
            SettlementCompletedEmailTemplate.htmlBody(
                item.getMember().getNickname(), crewTitle, item.getRefundAmount(), emailDeepLink));
      }
    } catch (RuntimeException e) {
      log.warn(
          "[이메일] 정산 완료 이메일 발송 실패 settlementId={}, memberUuid={}",
          item.getSettlement().getId(),
          item.getMember().getUuid(),
          e);
    }
  }
}
