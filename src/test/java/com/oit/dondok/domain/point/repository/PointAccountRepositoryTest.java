package com.oit.dondok.domain.point.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.config.JpaAuditingConfig;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.point.entity.PointAccount;
import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
@Import(JpaAuditingConfig.class)
class PointAccountRepositoryTest {

  @Autowired private TestEntityManager entityManager;
  @Autowired private PointAccountRepository pointAccountRepository;

  @Test
  void findByMemberIdReturnsPointAccount() {
    Member member = persistMember("member@example.com", "회원");
    PointAccount account = persistPointAccount(member);
    entityManager.flush();
    entityManager.clear();

    Optional<PointAccount> found = pointAccountRepository.findByMemberId(member.getId());

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(account.getId());
    assertThat(found.get().getMember().getId()).isEqualTo(member.getId());
  }

  @Test
  void findByMemberUuidReturnsPointAccount() {
    Member member = persistMember("member@example.com", "회원");
    PointAccount account = persistPointAccount(member);
    entityManager.flush();
    entityManager.clear();

    Optional<PointAccount> found = pointAccountRepository.findByMemberUuid(member.getUuid());

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(account.getId());
  }

  @Test
  void findByMemberUuidReturnsEmptyWhenAccountDoesNotExist() {
    Optional<PointAccount> found = pointAccountRepository.findByMemberUuid(UUID.randomUUID());

    assertThat(found).isEmpty();
  }

  @Test
  void findByMemberIdForUpdateReturnsPointAccount() {
    Member member = persistMember("member@example.com", "회원");
    PointAccount account = persistPointAccount(member);
    entityManager.flush();
    entityManager.clear();

    Optional<PointAccount> found = pointAccountRepository.findByMemberIdForUpdate(member.getId());

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(account.getId());
  }

  @Test
  void findByMemberIdForUpdateUsesPessimisticWriteLock() throws Exception {
    Method method = PointAccountRepository.class.getMethod("findByMemberIdForUpdate", Long.class);

    Lock lock = method.getAnnotation(Lock.class);

    assertThat(lock).isNotNull();
    assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
  }

  private Member persistMember(String email, String nickname) {
    return entityManager.persistAndFlush(Member.create(email, "password-hash", nickname));
  }

  private PointAccount persistPointAccount(Member member) {
    return entityManager.persistAndFlush(PointAccount.create(member));
  }
}
