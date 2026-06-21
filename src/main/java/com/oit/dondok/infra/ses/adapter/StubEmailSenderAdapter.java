package com.oit.dondok.infra.ses.adapter;

import com.oit.dondok.domain.notification.port.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("test | local | (integration & !integration-ses)")
public class StubEmailSenderAdapter implements EmailSender {

  @Override
  public void send(String toAddress, String subject, String htmlBody) {
    log.debug("[SES-STUB] 발송 생략 toAddress={} subject={}", toAddress, subject);
  }
}
