package com.oit.dondok.domain.point.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oit.dondok.config.JpaAuditingConfig;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.point.entity.PointCharge;
import com.oit.dondok.domain.point.entity.PointChargeStatus;
import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
@Import(JpaAuditingConfig.class)
class PointChargeRepositoryTest {

  @Autowired private TestEntityManager entityManager;
  @Autowired private PointChargeRepository pointChargeRepository;

  @Test
  void findByPaymentIdReturnsPointCharge() {
    Member member = persistMember("charge-member@example.com", "charge-member");
    PointCharge charge =
        pointChargeRepository.save(
            PointCharge.createPending(member, "payment-key", "order-id", 10_000L));
    entityManager.flush();
    entityManager.clear();

    Optional<PointCharge> found = pointChargeRepository.findByPaymentId("payment-key");

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(charge.getId());
    assertThat(found.get().getPaymentId()).isEqualTo("payment-key");
    assertThat(found.get().getStatus()).isEqualTo(PointChargeStatus.PENDING_CONFIRM);
  }

  @Test
  void findByPaymentIdForUpdateUsesPessimisticWriteLock() throws Exception {
    Method method = PointChargeRepository.class.getMethod("findByPaymentIdForUpdate", String.class);

    Lock lock = method.getAnnotation(Lock.class);

    assertThat(lock).isNotNull();
    assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
  }

  @Test
  void orderIdIsUnique() {
    Member member = persistMember("charge-order-member@example.com", "charge-order-member");
    pointChargeRepository.save(
        PointCharge.createPending(member, "payment-key-1", "order-id", 10_000L));
    entityManager.flush();

    assertThatThrownBy(
            () -> {
              pointChargeRepository.save(
                  PointCharge.createPending(member, "payment-key-2", "order-id", 10_000L));
              entityManager.flush();
            })
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private Member persistMember(String email, String nickname) {
    return entityManager.persistAndFlush(Member.create(email, "password-hash", nickname));
  }
}
