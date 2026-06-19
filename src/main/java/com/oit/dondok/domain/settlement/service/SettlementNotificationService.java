package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.notification.port.EmailSender;
import com.oit.dondok.domain.notification.port.NotificationPayload;
import com.oit.dondok.domain.notification.port.NotificationSender;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementItem;
import com.oit.dondok.infra.ses.template.SettlementCompletedEmailTemplate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
  private final NotificationSender notificationSender;
  private final EmailSender emailSender;

  // PointLedgerService.refundSettlement() AFTER_COMMIT 후 호출된다.
  // REQUIRES_NEW로 격리해 알림 실패가 정산 트랜잭션에 영향을 주지 않도록 한다.
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onSettlementRefundCredited(SettlementRefundCreditedNotificationEvent event) {
    try {
      notificationSender.send(
          event.member(),
          new NotificationPayload(
              "SETTLEMENT_REFUND_CREDITED",
              "settlement",
              String.valueOf(event.settlementId()),
              "dondok://settlements/" + event.settlementId() + "/me",
              event.refundAmount() + "원이 환급되었습니다.",
              null));
    } catch (RuntimeException e) {
      log.warn("[알림] 환급 완료 알림 발송 실패 settlementId={}", event.settlementId(), e);
    }
  }

  // TODO: 정산 배치 완료 후 LOCKED 참여자 전원에게 예상 환급금 변동을 알릴 때 사용 예정.
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
                "dondok://crews/" + crewId + "/settlement",
                crewTitle + " 크루 예상 환급금이 변동되었습니다.",
                crewTitle));
      } catch (RuntimeException e) {
        log.warn(
            "[알림] 예상 환급금 변동 알림 발송 실패 crewId={}, participantId={}", crewId, participant.getId(), e);
      }
    }
  }

  // TODO: 정산 배치 완료 시 호출 예정.
  // 정산 배치 완료 후 모든 참여자에게 정산 완료 알림을 발송한다.
  @Transactional
  public void sendSettlementCompletedNotifications(
      Settlement settlement, List<SettlementItem> items) {
    Long settlementId = settlement.getId();
    String crewTitle = settlement.getCrew().getTitle();
    for (SettlementItem item : items) {
      String deepLink = "dondok://settlements/" + settlementId + "/me";
      try {
        notificationSender.send(
            item.getMember(),
            new NotificationPayload(
                "SETTLEMENT_COMPLETED",
                "settlement",
                String.valueOf(settlementId),
                deepLink,
                crewTitle + " 크루 정산이 완료되었습니다.",
                crewTitle));
      } catch (RuntimeException e) {
        log.warn(
            "[FCM] 정산 완료 알림 발송 실패 settlementId={}, memberUuid={}",
            settlementId,
            item.getMember().getUuid(),
            e);
      }
      try {
        String memberEmail = item.getMember().getEmail();
        if (memberEmail != null && !memberEmail.isBlank()) {
          emailSender.send(
              memberEmail,
              SettlementCompletedEmailTemplate.subject(crewTitle),
              SettlementCompletedEmailTemplate.htmlBody(
                  item.getMember().getNickname(), crewTitle, item.getRefundAmount(), deepLink));
        }
      } catch (RuntimeException e) {
        log.warn(
            "[이메일] 정산 완료 이메일 발송 실패 settlementId={}, memberUuid={}",
            settlementId,
            item.getMember().getUuid(),
            e);
      }
    }
  }
}
