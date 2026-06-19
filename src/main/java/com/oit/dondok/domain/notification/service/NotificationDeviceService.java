package com.oit.dondok.domain.notification.service;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.domain.notification.dto.request.RegisterDeviceRequest;
import com.oit.dondok.domain.notification.dto.response.RegisterDeviceResponse;
import com.oit.dondok.domain.notification.entity.NotificationDevice;
import com.oit.dondok.domain.notification.repository.NotificationDeviceRepository;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationDeviceService {

  private final MemberRepository memberRepository;
  private final NotificationDeviceRepository notificationDeviceRepository;

  @Transactional
  public RegisterDeviceResponse registerDevice(UUID memberUuid, RegisterDeviceRequest request) {
    Member member =
        memberRepository
            .findByUuid(memberUuid)
            .orElseThrow(() -> new CustomException(GlobalErrorCode.NOT_FOUND));

    NotificationDevice device =
        notificationDeviceRepository
            .findByMemberAndDeviceId(member, request.deviceId())
            .map(
                existing -> {
                  existing.updateToken(request.fcmToken(), request.appVersion());
                  return existing;
                })
            .orElseGet(
                () -> {
                  try {
                    return notificationDeviceRepository.save(
                        NotificationDevice.create(
                            member,
                            request.deviceId(),
                            request.platform(),
                            request.fcmToken(),
                            request.appVersion()));
                  } catch (DataIntegrityViolationException e) {
                    NotificationDevice concurrent =
                        notificationDeviceRepository
                            .findByMemberAndDeviceId(member, request.deviceId())
                            .orElseThrow(() -> new CustomException(GlobalErrorCode.NOT_FOUND));
                    concurrent.updateToken(request.fcmToken(), request.appVersion());
                    return concurrent;
                  }
                });

    return RegisterDeviceResponse.from(device);
  }
}
