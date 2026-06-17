package com.oit.dondok.infra.ses.adapter;

import com.oit.dondok.domain.notification.port.EmailSender;
import com.oit.dondok.infra.ses.config.SesProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

@Slf4j
@Component
@Profile("!test & !integration")
@ConditionalOnExpression(
    "T(org.springframework.util.StringUtils).hasText('${app.ses.from-address:}')")
@RequiredArgsConstructor
public class SesEmailSenderAdapter implements EmailSender {

  private final SesClient sesClient;
  private final SesProperties sesProperties;

  @Override
  public void send(String toAddress, String subject, String htmlBody) {
    SendEmailRequest request =
        SendEmailRequest.builder()
            .destination(Destination.builder().toAddresses(toAddress).build())
            .message(
                Message.builder()
                    .subject(Content.builder().data(subject).charset("UTF-8").build())
                    .body(
                        Body.builder()
                            .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                            .build())
                    .build())
            .source(sesProperties.fromAddress())
            .build();
    sesClient.sendEmail(request);
    log.debug("[SES] 이메일 발송 완료 toAddress={} subject={}", toAddress, subject);
  }
}
