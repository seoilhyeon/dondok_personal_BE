package com.oit.dondok.domain.point.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oit.dondok.config.JpaAuditingConfig;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.point.entity.PointCharge;
import com.oit.dondok.domain.point.entity.PointChargeStatus;
import com.oit.dondok.domain.point.entity.PointHistory;
import com.oit.dondok.domain.point.entity.PointReferenceType;
import com.oit.dondok.domain.point.entity.PointTransactionType;
import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
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
  void findByIdForUpdateUsesPessimisticWriteLock() throws Exception {
    Method method = PointChargeRepository.class.getMethod("findByIdForUpdate", Long.class);

    Lock lock = method.getAnnotation(Lock.class);

    assertThat(lock).isNotNull();
    assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
  }

  @Test
  void findRecoveryTargetIdsReturnsOnlyStalePendingUnlinkedCharges() {
    Member member = persistMember("charge-recovery-member@example.com", "charge-recovery-member");
    PointCharge stalePending =
        persistCharge(member, "payment-key-1", "order-id-1", PointChargeStatus.PENDING_CONFIRM);
    PointCharge recentPending =
        persistCharge(member, "payment-key-2", "order-id-2", PointChargeStatus.PENDING_CONFIRM);
    PointCharge failedCharge =
        persistCharge(member, "payment-key-3", "order-id-3", PointChargeStatus.CONFIRM_FAILED);
    PointCharge completedCharge = persistCompletedCharge(member, "payment-key-4", "order-id-4");
    PointCharge retryWaitingCharge =
        persistCharge(member, "payment-key-5", "order-id-5", PointChargeStatus.PENDING_CONFIRM);
    PointCharge retryExhaustedCharge =
        persistCharge(member, "payment-key-6", "order-id-6", PointChargeStatus.PENDING_CONFIRM);
    entityManager.flush();
    setCreatedAt(stalePending.getId(), LocalDateTime.of(2026, 6, 18, 11, 54));
    setCreatedAt(recentPending.getId(), LocalDateTime.of(2026, 6, 18, 11, 59));
    setCreatedAt(failedCharge.getId(), LocalDateTime.of(2026, 6, 18, 11, 54));
    setCreatedAt(completedCharge.getId(), LocalDateTime.of(2026, 6, 18, 11, 54));
    setCreatedAt(retryWaitingCharge.getId(), LocalDateTime.of(2026, 6, 18, 11, 54));
    setCreatedAt(retryExhaustedCharge.getId(), LocalDateTime.of(2026, 6, 18, 11, 54));
    retryWaitingCharge.recordRecoveryAttempt(LocalDateTime.of(2026, 6, 18, 12, 5));
    org.springframework.test.util.ReflectionTestUtils.setField(
        retryExhaustedCharge, "recoveryAttemptCount", 12);
    entityManager.flush();
    entityManager.clear();

    List<Long> ids =
        pointChargeRepository.findRecoveryTargetIdsAfterId(
            PointChargeStatus.PENDING_CONFIRM,
            LocalDateTime.of(2026, 6, 18, 11, 55),
            0L,
            12,
            LocalDateTime.of(2026, 6, 18, 12, 0),
            Pageable.ofSize(50));

    assertThat(ids).containsExactly(stalePending.getId());
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

  private PointCharge persistCharge(
      Member member, String paymentId, String orderId, PointChargeStatus status) {
    PointCharge charge = PointCharge.createPending(member, paymentId, orderId, 10_000L);
    if (status == PointChargeStatus.CONFIRM_FAILED) {
      charge.fail("PAYMENT_CONFIRM_FAILED", "failed");
    }
    return entityManager.persistAndFlush(charge);
  }

  private PointCharge persistCompletedCharge(Member member, String paymentId, String orderId) {
    PointCharge charge = PointCharge.createPending(member, paymentId, orderId, 10_000L);
    PointHistory history =
        entityManager.persistAndFlush(
            PointHistory.create(
                member,
                10_000L,
                10_000L,
                0L,
                0L,
                PointTransactionType.POINT_CHARGE,
                PointReferenceType.POINT_CHARGE,
                0L,
                "charge:%s".formatted(paymentId)));
    charge.complete(history);
    return entityManager.persistAndFlush(charge);
  }

  private void setCreatedAt(Long id, LocalDateTime createdAt) {
    entityManager
        .getEntityManager()
        .createNativeQuery("update point_charge set created_at = ? where id = ?")
        .setParameter(1, createdAt)
        .setParameter(2, id)
        .executeUpdate();
  }
}
