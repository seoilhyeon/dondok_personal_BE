package com.oit.dondok.infra.fcm.adapter;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.notification.port.NotificationPayload;
import org.junit.jupiter.api.Test;

class StubNotificationSenderTest {

  private final StubNotificationSender sut = new StubNotificationSender();

  private static final NotificationPayload PAYLOAD =
      new NotificationPayload("CREW_CERT", "CREW", "42", "/crew/42", "미션 인증 완료!");

  @Test
  void send_doesNotThrowAnyException() {
    Member member = Member.create("test@example.com", null, "테스터");

    assertThatCode(() -> sut.send(member, PAYLOAD)).doesNotThrowAnyException();
  }
}
