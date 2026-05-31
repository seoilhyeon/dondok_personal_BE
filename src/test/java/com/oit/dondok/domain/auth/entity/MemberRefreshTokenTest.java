package com.oit.dondok.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oit.dondok.domain.member.entity.Member;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MemberRefreshTokenTest {

  private static final Member MEMBER = Member.create("user@example.com", "encoded-pw", "tester");
  private static final String TOKEN_HASH = "a".repeat(64);
  private static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(7);

  @Test
  void createSucceedsWithValidInputs() {
    MemberRefreshToken token = MemberRefreshToken.create(MEMBER, TOKEN_HASH, FUTURE);

    assertThat(token.getMember()).isSameAs(MEMBER);
    assertThat(token.getTokenHash()).isEqualTo(TOKEN_HASH);
    assertThat(token.getExpiresAt()).isEqualTo(FUTURE);
  }

  @Test
  void createRejectsNullMember() {
    assertThatThrownBy(() -> MemberRefreshToken.create(null, TOKEN_HASH, FUTURE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("member");
  }

  @Test
  void createRejectsNullTokenHash() {
    assertThatThrownBy(() -> MemberRefreshToken.create(MEMBER, null, FUTURE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tokenHash");
  }

  @Test
  void createRejectsBlankTokenHash() {
    assertThatThrownBy(() -> MemberRefreshToken.create(MEMBER, "   ", FUTURE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tokenHash");
  }

  @Test
  void createRejectsNullExpiresAt() {
    assertThatThrownBy(() -> MemberRefreshToken.create(MEMBER, TOKEN_HASH, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expiresAt");
  }

  @Test
  void createRejectsPastExpiresAt() {
    LocalDateTime past = LocalDateTime.now().minusSeconds(1);

    assertThatThrownBy(() -> MemberRefreshToken.create(MEMBER, TOKEN_HASH, past))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expiresAt");
  }
}
