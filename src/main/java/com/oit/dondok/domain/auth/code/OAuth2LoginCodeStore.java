package com.oit.dondok.domain.auth.code;

import com.oit.dondok.domain.auth.exception.AuthErrorCode;
import com.oit.dondok.global.exception.CustomException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class OAuth2LoginCodeStore {

  private static final Duration CODE_TTL = Duration.ofMinutes(3);

  private final Map<String, OAuth2LoginCode> loginCodes = new ConcurrentHashMap<>();

  /** access token 교환에 사용할 1회용 로그인 코드를 발급한다. */
  public String issue(UUID memberUuid) {
    LocalDateTime now = LocalDateTime.now();
    removeExpiredCodes(now);

    String code = UUID.randomUUID().toString();
    loginCodes.put(code, new OAuth2LoginCode(code, memberUuid, now.plus(CODE_TTL)));
    return code;
  }

  /** 1회용 로그인 코드를 검증하고 저장된 회원 UUID를 반환한다. */
  public UUID consume(String code) {
    if (code == null || code.isBlank()) {
      throw new CustomException(AuthErrorCode.OAUTH_LOGIN_CODE_INVALID);
    }

    OAuth2LoginCode loginCode = loginCodes.remove(code);
    if (loginCode == null || loginCode.isExpired(LocalDateTime.now())) {
      throw new CustomException(AuthErrorCode.OAUTH_LOGIN_CODE_INVALID);
    }
    return loginCode.memberUuid();
  }

  /** 만료된 로그인 코드를 메모리에서 정리한다. */
  private void removeExpiredCodes(LocalDateTime now) {
    loginCodes.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
  }
}
