package com.oit.dondok.infra.ses.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.oit.dondok.IntegrationTest;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.notification.port.EmailSender;
import com.oit.dondok.domain.notification.port.NotificationSender;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementItem;
import com.oit.dondok.domain.settlement.service.SettlementNotificationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * 실제 AWS SES를 통해 정산 완료 이메일을 발송하는 통합 테스트.
 *
 * <p>실행 전 환경변수 설정 필수:
 *
 * <pre>
 *   SES_FROM_ADDRESS      - SES에서 검증된 발신자 주소 (예: noreply@dondok.kr)
 *   SES_TEST_RECIPIENT    - 수신 확인용 이메일 주소
 *   AWS_ACCESS_KEY_ID     - SES SendEmail 권한 보유 IAM 자격증명
 *   AWS_SECRET_ACCESS_KEY
 *   AWS_REGION            - (선택) 기본값 ap-northeast-2
 * </pre>
 *
 * 시연/테스트 시 {@code @Disabled} 제거 후 실행.
 */
@Disabled("실제 AWS SES 호출 — 환경변수 설정 후 @Disabled 제거하여 실행")
@IntegrationTest
@ActiveProfiles({"integration", "integration-ses"})
class SettlementCompletedEmailSesIntegrationTest {

  @Autowired private SettlementNotificationService settlementNotificationService;
  @Autowired private EmailSender emailSender;
  @MockBean private NotificationSender notificationSender;

  @Test
  void emailSenderIsBoundToRealSesAdapter() {
    assertThat(emailSender).isInstanceOf(SesEmailSenderAdapter.class);
  }

  @Test
  void sendsSettlementCompletedEmailViaRealSes() {
    String recipient = System.getenv("SES_TEST_RECIPIENT");
    //  System.out.println(">>> SES_TEST_RECIPIENT = " + recipient);
    assertThat(recipient).as("SES_TEST_RECIPIENT 환경변수가 설정되어 있어야 합니다.").isNotBlank();

    Member member = mock(Member.class);
    given(member.getUuid()).willReturn(UUID.randomUUID());
    given(member.getNickname()).willReturn("돈독테스터");
    given(member.getEmail()).willReturn(recipient);

    SettlementItem item = mock(SettlementItem.class);
    given(item.getMember()).willReturn(member);
    given(item.getRefundAmount()).willReturn(15_000L);

    Crew crew = mock(Crew.class);
    given(crew.getId()).willReturn(1L);
    given(crew.getTitle()).willReturn("SES 발송 테스트 크루");

    Settlement settlement = mock(Settlement.class);
    given(settlement.getId()).willReturn(1L);
    given(settlement.getCrew()).willReturn(crew);

    assertThatNoException()
        .isThrownBy(
            () ->
                settlementNotificationService.sendSettlementCompletedNotifications(
                    settlement, List.of(item)));
  }
}
