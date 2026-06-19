package com.oit.dondok.domain.notification.service;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.domain.notification.dto.request.NotificationSettingsRequest;
import com.oit.dondok.domain.notification.dto.response.NotificationSettingsResponse;
import com.oit.dondok.domain.notification.entity.NotificationSettings;
import com.oit.dondok.domain.notification.exception.NotificationErrorCode;
import com.oit.dondok.domain.notification.repository.NotificationSettingsRepository;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import com.oit.dondok.infra.auth.exception.SecurityErrorCode;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationSettingsService {

  private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

  private final NotificationSettingsRepository notificationSettingsRepository;
  private final MemberRepository memberRepository;

  @Transactional(readOnly = true)
  public NotificationSettingsResponse getSettings(UUID memberUuid) {
    if (memberUuid == null) {
      throw new CustomException(SecurityErrorCode.UNAUTHORIZED);
    }
    return notificationSettingsRepository
        .findByMemberUuid(memberUuid)
        .map(NotificationSettingsResponse::from)
        .orElse(NotificationSettingsResponse.defaults());
  }

  @Transactional
  public NotificationSettingsResponse saveSettings(
      UUID memberUuid, NotificationSettingsRequest request) {
    if (memberUuid == null) {
      throw new CustomException(SecurityErrorCode.UNAUTHORIZED);
    }
    validateQuietHours(request.quietStartTime(), request.quietEndTime());

    LocalTime quietStart = parseTime(request.quietStartTime());
    LocalTime quietEnd = parseTime(request.quietEndTime());

    NotificationSettings settings =
        notificationSettingsRepository
            .findByMemberUuid(memberUuid)
            .orElseGet(
                () -> {
                  Member member =
                      memberRepository
                          .findByUuid(memberUuid)
                          .orElseThrow(() -> new CustomException(GlobalErrorCode.NOT_FOUND));
                  return notificationSettingsRepository.save(
                      NotificationSettings.createDefault(member));
                });

    settings.update(request.categories(), quietStart, quietEnd);
    return NotificationSettingsResponse.from(settings);
  }

  private void validateQuietHours(String start, String end) {
    boolean hasStart = start != null && !start.isBlank();
    boolean hasEnd = end != null && !end.isBlank();
    if (hasStart != hasEnd) {
      throw new CustomException(NotificationErrorCode.INVALID_QUIET_HOURS);
    }
  }

  private LocalTime parseTime(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return LocalTime.parse(value, HH_MM);
  }
}
