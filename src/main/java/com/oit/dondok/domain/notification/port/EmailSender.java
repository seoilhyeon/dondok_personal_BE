package com.oit.dondok.domain.notification.port;

public interface EmailSender {

  void send(String toAddress, String subject, String htmlBody);
}
