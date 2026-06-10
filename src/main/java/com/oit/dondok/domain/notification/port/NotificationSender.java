package com.oit.dondok.domain.notification.port;

import com.oit.dondok.domain.member.entity.Member;

public interface NotificationSender {

  void send(Member member, NotificationPayload payload);
}
