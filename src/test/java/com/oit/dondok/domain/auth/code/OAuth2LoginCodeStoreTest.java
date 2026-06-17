package com.oit.dondok.domain.auth.code;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oit.dondok.domain.auth.exception.AuthErrorCode;
import com.oit.dondok.global.exception.CustomException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OAuth2LoginCodeStoreTest {

  private final OAuth2LoginCodeStore store = new OAuth2LoginCodeStore();

  @Test
  void issueAndConsumeReturnsStoredMemberUuidOnce() {
    UUID memberUuid = UUID.randomUUID();
    String code = store.issue(memberUuid);

    UUID consumedMemberUuid = store.consume(code);

    assertThat(consumedMemberUuid).isEqualTo(memberUuid);
    assertThatThrownBy(() -> store.consume(code))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(AuthErrorCode.OAUTH_LOGIN_CODE_INVALID));
  }

  @Test
  void consumeRejectsBlankCode() {
    assertThatThrownBy(() -> store.consume(" "))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(AuthErrorCode.OAUTH_LOGIN_CODE_INVALID));
  }
}
