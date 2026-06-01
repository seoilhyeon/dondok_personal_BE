package com.oit.dondok.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.IntegrationTest;
import com.oit.dondok.domain.auth.exception.AuthErrorCode;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.global.exception.CustomException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

@IntegrationTest
class AuthServiceConcurrencyIntegrationTest {

  @Autowired private AuthService authService;

  @Autowired private MemberRepository memberRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  void refreshAllowsOnlyOneConcurrentRotationForSameToken() throws Exception {
    String email = "refresh-" + UUID.randomUUID() + "@example.com";
    String password = "raw-password";
    memberRepository.save(Member.create(email, passwordEncoder.encode(password), "tester"));
    LoginResult loginResult = authService.login(email, password);
    String refreshToken = loginResult.refreshToken();

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    Callable<Boolean> refreshTask =
        () -> {
          ready.countDown();
          start.await();

          try {
            authService.refresh(refreshToken);
            return true;
          } catch (CustomException exception) {
            assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.REFRESH_TOKEN_INVALID);
            return false;
          }
        };

    try {
      Future<Boolean> first = executor.submit(refreshTask);
      Future<Boolean> second = executor.submit(refreshTask);

      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      List<Boolean> results =
          List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

      assertThat(results).containsExactlyInAnyOrder(true, false);
    } finally {
      executor.shutdownNow();
    }
  }
}
