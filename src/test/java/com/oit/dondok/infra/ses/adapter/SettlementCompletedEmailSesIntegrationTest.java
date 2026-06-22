package com.oit.dondok.infra.ses.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.oit.dondok.IntegrationTest;
import com.oit.dondok.domain.notification.port.EmailSender;
import com.oit.dondok.domain.notification.port.NotificationSender;
import com.oit.dondok.infra.ses.template.SettlementCompletedEmailTemplate;
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

  @Autowired private EmailSender emailSender;
  @MockBean private NotificationSender notificationSender;

  @Test
  void emailSenderIsBoundToRealSesAdapter() {
    assertThat(emailSender).isInstanceOf(SesEmailSenderAdapter.class);
  }

  @Test
  void sendsSettlementCompletedEmailViaRealSes() {
    String recipient = System.getenv("SES_TEST_RECIPIENT");
    assertThat(recipient).as("SES_TEST_RECIPIENT 환경변수가 설정되어 있어야 합니다.").isNotBlank();

    String crewTitle = "테스트 크루";
    String subject = SettlementCompletedEmailTemplate.subject(crewTitle);
    String htmlBody =
        SettlementCompletedEmailTemplate.htmlBody(
            "테스트유저", crewTitle, 12_500L, "https://dondok-fe.vercel.app/crews/1/settlement");

    assertThatNoException().isThrownBy(() -> emailSender.send(recipient, subject, htmlBody));
  }
}
