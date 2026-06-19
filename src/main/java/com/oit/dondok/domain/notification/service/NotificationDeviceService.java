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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationDeviceService {

  private final MemberRepository memberRepository;
  private final NotificationDeviceRepository notificationDeviceRepository;

  // 순환참조 없이 REQUIRES_NEW 트랜잭션 분리를 위한 self-proxy 주입
  @Lazy @Autowired private NotificationDeviceService self;

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
                    return self.saveNewDevice(member, request);
                  } catch (DataIntegrityViolationException e) {
                    return self.findExistingDevice(member, request.deviceId());
                  }
                });

    return RegisterDeviceResponse.from(device);
  }

  // saveAndFlush로 즉시 flush → constraint violation을 caller의 catch에서 잡을 수 있도록 함
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public NotificationDevice saveNewDevice(Member member, RegisterDeviceRequest request) {
    return notificationDeviceRepository.saveAndFlush(
        NotificationDevice.create(
            member,
            request.deviceId(),
            request.platform(),
            request.fcmToken(),
            request.appVersion()));
  }

  // 경합 후 재조회도 REQUIRES_NEW로 격리 — 외부 트랜잭션 오염 방지
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public NotificationDevice findExistingDevice(Member member, String deviceId) {
    return notificationDeviceRepository
        .findByMemberAndDeviceId(member, deviceId)
        .orElseThrow(() -> new CustomException(GlobalErrorCode.NOT_FOUND));
  }
}
