package com.oit.dondok.domain.auth.code;

import java.time.LocalDateTime;
import java.util.UUID;

public record OAuth2LoginCode(String code, UUID memberUuid, LocalDateTime expiresAt) {

  /** 현재 시각 기준으로 로그인 코드가 만료되었는지 확인한다. */
  public boolean isExpired(LocalDateTime now) {
    return !expiresAt.isAfter(now);
  }
}
