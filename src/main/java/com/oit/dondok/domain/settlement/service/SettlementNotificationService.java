package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.notification.port.NotificationPayload;
import com.oit.dondok.domain.notification.port.NotificationSender;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementItem;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementNotificationService {

  private final CrewParticipantRepository crewParticipantRepository;
  private final NotificationSender notificationSender;

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
                "'" + crewTitle + "' 크루 예상 환급금이 변동되었습니다."));
      } catch (RuntimeException e) {
        log.warn(
            "[알림] 예상 환급금 변동 알림 발송 실패 crewId={}, participantId={}", crewId, participant.getId(), e);
      }
    }
  }

  // 정산 배치 완료 후 모든 참여자에게 정산 완료 알림을 발송한다.
  @Transactional
  public void sendSettlementCompletedNotifications(
      Settlement settlement, List<SettlementItem> items) {
    Long settlementId = settlement.getId();
    String crewTitle = settlement.getCrew().getTitle();
    for (SettlementItem item : items) {
      try {
        notificationSender.send(
            item.getMember(),
            new NotificationPayload(
                "SETTLEMENT_COMPLETED",
                "settlement",
                String.valueOf(settlementId),
                "dondok://settlements/" + settlementId + "/me",
                "'" + crewTitle + "' 크루 정산이 완료되었습니다."));
      } catch (RuntimeException e) {
        log.warn(
            "[알림] 정산 완료 알림 발송 실패 settlementId={}, memberId={}",
            settlementId,
            item.getMember().getId(),
            e);
      }
    }
  }
}
