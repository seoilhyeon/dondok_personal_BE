package com.oit.dondok.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

class MemberRefreshTokenRepositoryTest {

  @Test
  void findByTokenHashUsesPessimisticWriteLock() throws Exception {
    Method method = MemberRefreshTokenRepository.class.getMethod("findByTokenHash", String.class);

    Lock lock = method.getAnnotation(Lock.class);

    assertThat(lock).isNotNull();
    assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
  }
}
