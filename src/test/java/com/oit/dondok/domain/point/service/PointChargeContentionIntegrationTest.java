package com.oit.dondok.domain.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oit.dondok.IntegrationTest;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.domain.point.dto.request.PointChargeRequest;
import com.oit.dondok.domain.point.entity.PointAccount;
import com.oit.dondok.domain.point.entity.PointCharge;
import com.oit.dondok.domain.point.entity.PointChargeStatus;
import com.oit.dondok.domain.point.exception.PointErrorCode;
import com.oit.dondok.domain.point.port.PaymentConfirmRequest;
import com.oit.dondok.domain.point.port.PaymentConfirmResult;
import com.oit.dondok.domain.point.repository.PointAccountRepository;
import com.oit.dondok.domain.point.repository.PointChargeRepository;
import com.oit.dondok.domain.point.repository.PointHistoryRepository;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.infra.payment.TossPaymentsConfirmClient;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
@TestPropertySource(
    properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
class PointChargeContentionIntegrationTest {

  private static final String PAYMENT_A = "gap-payment-4000";
  private static final String PAYMENT_B = "gap-payment-6000";
  private static final String ORDER_A = "gap-order-4000";
  private static final String ORDER_B = "gap-order-6000";

  @Autowired private DataSource dataSource;
  @Autowired private MemberRepository memberRepository;
  @Autowired private PointAccountRepository pointAccountRepository;
  @Autowired private PointHistoryRepository pointHistoryRepository;
  @Autowired private PointChargeService pointChargeService;
  @Autowired private PlatformTransactionManager transactionManager;
  @PersistenceContext private EntityManager entityManager;

  @SpyBean private PointChargeRepository pointChargeRepository;
  @MockBean private TossPaymentsConfirmClient tossPaymentsConfirmClient;

  private final ThreadLocal<String> workerLabel = new ThreadLocal<>();

  @Test
  @Timeout(30)
  void concurrentDistinctNewChargesCompleteWithoutDatabaseDeadlock() throws Exception {
    assertMysqlContract();
    UUID memberUuid = seedGapAndAccount();
    Set<String> targetPayments = Set.of(PAYMENT_A, PAYMENT_B);
    CyclicBarrier insertsReady = new CyclicBarrier(2);
    AtomicInteger targetSaves = new AtomicInteger();
    AtomicInteger confirms = new AtomicInteger();

    synchronizeTargetSaves(targetPayments, insertsReady, targetSaves, null);
    when(tossPaymentsConfirmClient.confirm(any(PaymentConfirmRequest.class)))
        .thenAnswer(
            invocation -> {
              PaymentConfirmRequest request = invocation.getArgument(0);
              confirms.incrementAndGet();
              return new PaymentConfirmResult(
                  request.paymentId(), request.orderId(), request.amount(), "KRW", "DONE");
            });

    List<ChargeOutcome> outcomes =
        runConcurrently(
            memberUuid,
            new PointChargeRequest(PAYMENT_A, ORDER_A, 10_000L),
            new PointChargeRequest(PAYMENT_B, ORDER_B, 20_000L));

    assertThat(targetSaves).hasValue(2);
    assertThat(confirms).hasValue(2);

    for (ChargeOutcome outcome : outcomes) {
      if (outcome.failure() != null) {
        fail("concurrent charge failed: " + databaseSignature(outcome.failure()));
      }
    }

    assertThat(outcomes).extracting(ChargeOutcome::result).allMatch(PointChargeResult::created);
    assertPersistedDistinctCharges(memberUuid);
    verify(tossPaymentsConfirmClient, never()).cancel(any(), any());
  }

  @Test
  @Timeout(30)
  void concurrentSamePaymentResolvesWinnerAfterRollbackInFreshTransaction() throws Exception {
    UUID memberUuid = seedAccount("same-payment");
    String paymentId = "same-payment-key";
    String orderId = "same-payment-order";
    PointChargeRequest request = new PointChargeRequest(paymentId, orderId, 15_000L);
    Map<String, Integer> transactionCompletions = new ConcurrentHashMap<>();
    Map<String, Boolean> freshTransactionActiveByWorker = new ConcurrentHashMap<>();
    Map<String, AtomicInteger> confirmsByWorker = new ConcurrentHashMap<>();

    synchronizeTargetSaves(
        Set.of(paymentId), new CyclicBarrier(2), new AtomicInteger(), transactionCompletions);
    stubLockedPaymentLookup(transactionCompletions, freshTransactionActiveByWorker);
    when(tossPaymentsConfirmClient.confirm(any(PaymentConfirmRequest.class)))
        .thenAnswer(
            invocation -> {
              PaymentConfirmRequest confirm = invocation.getArgument(0);
              confirmsByWorker
                  .computeIfAbsent(workerLabel.get(), ignored -> new AtomicInteger())
                  .incrementAndGet();
              return new PaymentConfirmResult(
                  confirm.paymentId(), confirm.orderId(), confirm.amount(), "KRW", "DONE");
            });

    List<ChargeOutcome> outcomes = runConcurrently(memberUuid, request, request);

    assertNoUnexpectedFailures(outcomes);
    assertThat(outcomes)
        .extracting(ChargeOutcome::result)
        .extracting(PointChargeResult::created)
        .containsExactlyInAnyOrder(true, false);
    assertThat(outcomes.get(0).result().response().pointHistoryId())
        .isEqualTo(outcomes.get(1).result().response().pointHistoryId());
    assertThat(transactionCompletions).containsValue(TransactionSynchronization.STATUS_ROLLED_BACK);
    assertThat(freshTransactionActiveByWorker).containsValue(true);
    assertThat(confirmCount(confirmsByWorker, "first")).isBetween(0, 1);
    assertThat(confirmCount(confirmsByWorker, "second")).isBetween(0, 1);
    assertThat(confirmCount(confirmsByWorker, "first") + confirmCount(confirmsByWorker, "second"))
        .isGreaterThanOrEqualTo(1);
    assertSingleChargeState(memberUuid, paymentId, 15_000L);
    verify(tossPaymentsConfirmClient, never()).cancel(any(), any());
  }

  @Test
  @Timeout(30)
  void concurrentDifferentPaymentsWithSameOrderLeaveOneWinner() throws Exception {
    UUID memberUuid = seedAccount("same-order");
    String paymentA = "same-order-payment-a";
    String paymentB = "same-order-payment-b";
    String orderId = "shared-order";
    PointChargeRequest firstRequest = new PointChargeRequest(paymentA, orderId, 10_000L);
    PointChargeRequest secondRequest = new PointChargeRequest(paymentB, orderId, 20_000L);
    Map<String, Integer> transactionCompletions = new ConcurrentHashMap<>();
    Map<String, Boolean> freshTransactionActiveByWorker = new ConcurrentHashMap<>();
    Map<String, AtomicInteger> confirmsByPayment = new ConcurrentHashMap<>();

    synchronizeTargetSaves(
        Set.of(paymentA, paymentB),
        new CyclicBarrier(2),
        new AtomicInteger(),
        transactionCompletions);
    stubLockedPaymentLookup(transactionCompletions, freshTransactionActiveByWorker);
    when(tossPaymentsConfirmClient.confirm(any(PaymentConfirmRequest.class)))
        .thenAnswer(
            invocation -> {
              PaymentConfirmRequest confirm = invocation.getArgument(0);
              confirmsByPayment
                  .computeIfAbsent(confirm.paymentId(), ignored -> new AtomicInteger())
                  .incrementAndGet();
              return new PaymentConfirmResult(
                  confirm.paymentId(), confirm.orderId(), confirm.amount(), "KRW", "DONE");
            });

    List<ChargeOutcome> outcomes = runConcurrently(memberUuid, firstRequest, secondRequest);

    assertThat(outcomes.stream().filter(outcome -> outcome.result() != null)).hasSize(1);
    List<Throwable> failures =
        outcomes.stream().map(ChargeOutcome::failure).filter(Objects::nonNull).toList();
    assertThat(failures).hasSize(1);
    assertThat(failures.get(0)).isInstanceOf(CustomException.class);
    assertThat(((CustomException) failures.get(0)).getErrorCode())
        .isEqualTo(PointErrorCode.IDEMPOTENCY_CONFLICT);
    assertThat(transactionCompletions).containsValue(TransactionSynchronization.STATUS_ROLLED_BACK);
    assertThat(freshTransactionActiveByWorker).containsValue(true);

    String winner =
        new TransactionTemplate(transactionManager)
            .execute(
                status -> {
                  Optional<PointCharge> first = pointChargeRepository.findByPaymentId(paymentA);
                  Optional<PointCharge> second = pointChargeRepository.findByPaymentId(paymentB);
                  assertThat(Stream.of(first, second).filter(Optional::isPresent)).hasSize(1);
                  return first.isPresent() ? paymentA : paymentB;
                });
    String loser = winner.equals(paymentA) ? paymentB : paymentA;
    long winnerAmount = winner.equals(paymentA) ? 10_000L : 20_000L;

    assertThat(confirmCount(confirmsByPayment, winner)).isEqualTo(1);
    assertThat(confirmCount(confirmsByPayment, loser)).isZero();
    assertSingleChargeState(memberUuid, winner, winnerAmount);
    assertThat(pointHistoryRepository.findByIdempotencyKey("charge:" + loser)).isEmpty();
    verify(tossPaymentsConfirmClient, never()).cancel(any(), any());
  }

  private void synchronizeTargetSaves(
      Set<String> targetPayments,
      CyclicBarrier insertsReady,
      AtomicInteger targetSaves,
      Map<String, Integer> transactionCompletions) {
    doAnswer(
            invocation -> {
              PointCharge charge = invocation.getArgument(0);
              if (targetPayments.contains(charge.getPaymentId())) {
                targetSaves.incrementAndGet();
                if (transactionCompletions != null) {
                  String label = workerLabel.get();
                  TransactionSynchronizationManager.registerSynchronization(
                      new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                          transactionCompletions.put(label, status);
                        }
                      });
                }
                insertsReady.await(5, TimeUnit.SECONDS);
              }
              return persistAndFlush(charge);
            })
        .when(pointChargeRepository)
        .save(any(PointCharge.class));
  }

  private void stubLockedPaymentLookup(
      Map<String, Integer> transactionCompletions,
      Map<String, Boolean> freshTransactionActiveByWorker) {
    doAnswer(
            invocation -> {
              recordFreshTransactionAfterRollback(
                  transactionCompletions, freshTransactionActiveByWorker);
              return findChargeForUpdate(invocation.getArgument(0));
            })
        .when(pointChargeRepository)
        .findByPaymentIdForUpdate(any());
  }

  private void recordFreshTransactionAfterRollback(
      Map<String, Integer> transactionCompletions,
      Map<String, Boolean> freshTransactionActiveByWorker) {
    String label = workerLabel.get();
    if (label != null
        && Integer.valueOf(TransactionSynchronization.STATUS_ROLLED_BACK)
            .equals(transactionCompletions.get(label))) {
      freshTransactionActiveByWorker.putIfAbsent(
          label, TransactionSynchronizationManager.isActualTransactionActive());
    }
  }

  private Optional<PointCharge> findChargeForUpdate(String paymentId) {
    return entityManager
        .createQuery(
            "select charge from PointCharge charge where charge.paymentId = :paymentId",
            PointCharge.class)
        .setParameter("paymentId", paymentId)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .getResultStream()
        .findFirst();
  }

  private ChargeOutcome chargeAfterStart(
      String label,
      CountDownLatch workersReady,
      CountDownLatch start,
      UUID memberUuid,
      PointChargeRequest request) {
    workersReady.countDown();
    workerLabel.set(label);
    try {
      if (!start.await(5, TimeUnit.SECONDS)) {
        return new ChargeOutcome(null, new IllegalStateException("start gate timed out"));
      }
      return new ChargeOutcome(pointChargeService.charge(memberUuid, request), null);
    } catch (Throwable failure) {
      return new ChargeOutcome(null, failure);
    } finally {
      workerLabel.remove();
    }
  }

  private List<ChargeOutcome> runConcurrently(
      UUID memberUuid, PointChargeRequest firstRequest, PointChargeRequest secondRequest)
      throws Exception {
    CountDownLatch workersReady = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<ChargeOutcome> first =
          executor.submit(
              () -> chargeAfterStart("first", workersReady, start, memberUuid, firstRequest));
      Future<ChargeOutcome> second =
          executor.submit(
              () -> chargeAfterStart("second", workersReady, start, memberUuid, secondRequest));

      assertThat(workersReady.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
    } finally {
      start.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  // The save spy bypasses Spring Data's proxy, so preserve its constraint-violation contract
  private PointCharge persistAndFlush(PointCharge charge) {
    try {
      entityManager.persist(charge);
      entityManager.flush();
      return charge;
    } catch (RuntimeException failure) {
      SQLException sqlException = findSqlException(failure);
      if (sqlException != null && sqlException.getErrorCode() == 1062) {
        throw new DataIntegrityViolationException("duplicate key", failure);
      }
      DataAccessException translated =
          EntityManagerFactoryUtils.convertJpaAccessExceptionIfPossible(failure);
      if (translated != null) {
        throw translated;
      }
      throw failure;
    }
  }

  private UUID seedGapAndAccount() {
    AtomicReference<UUID> memberUuid = new AtomicReference<>();
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              Member target =
                  memberRepository.save(
                      Member.create(
                          "point-contention@example.com", "password", "point-contention"));
              pointAccountRepository.save(PointAccount.create(target));
              memberUuid.set(target.getUuid());

              // Keep the target payment IDs in one committed payment_id unique-index gap.
              // The old missing-row FOR UPDATE path used this layout to reproduce MySQL 1213/40001.
              Member sentinel =
                  memberRepository.save(
                      Member.create("point-gap@example.com", "password", "point-gap"));
              pointChargeRepository.save(
                  PointCharge.createPending(
                      sentinel, "gap-payment-0000", "gap-order-0000", 1_000L));
              pointChargeRepository.save(
                  PointCharge.createPending(
                      sentinel, "gap-payment-9999", "gap-order-9999", 1_000L));
            });
    return memberUuid.get();
  }

  private UUID seedAccount(String suffix) {
    AtomicReference<UUID> memberUuid = new AtomicReference<>();
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              Member member =
                  memberRepository.save(
                      Member.create(
                          "point-" + suffix + "@example.com", "password", "point-" + suffix));
              pointAccountRepository.save(PointAccount.create(member));
              memberUuid.set(member.getUuid());
            });
    return memberUuid.get();
  }

  private void assertPersistedDistinctCharges(UUID memberUuid) {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              PointCharge first = pointChargeRepository.findByPaymentId(PAYMENT_A).orElseThrow();
              PointCharge second = pointChargeRepository.findByPaymentId(PAYMENT_B).orElseThrow();
              PointAccount account =
                  pointAccountRepository.findByMemberUuid(memberUuid).orElseThrow();

              assertThat(first.getStatus()).isEqualTo(PointChargeStatus.COMPLETED);
              assertThat(second.getStatus()).isEqualTo(PointChargeStatus.COMPLETED);
              assertThat(first.getPointHistory().getId())
                  .isNotEqualTo(second.getPointHistory().getId());
              assertThat(pointHistoryRepository.findByIdempotencyKey("charge:" + PAYMENT_A))
                  .isPresent();
              assertThat(pointHistoryRepository.findByIdempotencyKey("charge:" + PAYMENT_B))
                  .isPresent();
              assertThat(account.getAvailableBalance()).isEqualTo(30_000L);
              assertThat(account.getReservedBalance()).isZero();
              assertThat(account.getLockedBalance()).isZero();
            });
  }

  private void assertSingleChargeState(UUID memberUuid, String paymentId, long amount) {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              PointCharge charge = pointChargeRepository.findByPaymentId(paymentId).orElseThrow();
              PointAccount account =
                  pointAccountRepository.findByMemberUuid(memberUuid).orElseThrow();

              assertThat(charge.getStatus()).isEqualTo(PointChargeStatus.COMPLETED);
              assertThat(charge.getPointHistory()).isNotNull();
              assertThat(pointHistoryRepository.findByIdempotencyKey("charge:" + paymentId))
                  .isPresent();
              assertThat(account.getAvailableBalance()).isEqualTo(amount);
              assertThat(account.getReservedBalance()).isZero();
              assertThat(account.getLockedBalance()).isZero();
            });
  }

  private void assertNoUnexpectedFailures(List<ChargeOutcome> outcomes) {
    for (ChargeOutcome outcome : outcomes) {
      if (outcome.failure() != null) {
        fail("concurrent charge failed: " + databaseSignature(outcome.failure()));
      }
    }
  }

  private int confirmCount(Map<String, AtomicInteger> counts, String key) {
    AtomicInteger count = counts.get(key);
    return count == null ? 0 : count.get();
  }

  private void assertMysqlContract() throws SQLException {
    try (var connection = dataSource.getConnection()) {
      assertThat(connection.getMetaData().getURL()).startsWith("jdbc:mysql:");
      assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(8);
    }
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    assertThat(jdbc.queryForObject("select @@transaction_isolation", String.class))
        .isEqualToIgnoringCase("REPEATABLE-READ");
    Integer uniqueIndexes =
        jdbc.queryForObject(
            """
            select count(*)
            from information_schema.statistics
            where table_schema = database()
              and table_name = 'point_charge'
              and index_name in ('uk_point_charge_payment_id', 'uk_point_charge_order_id')
              and non_unique = 0
            """,
            Integer.class);
    assertThat(uniqueIndexes).isEqualTo(2);
  }

  private String databaseSignature(Throwable failure) {
    String type = failure.getClass().getSimpleName();
    SQLException sqlException = findSqlException(failure);
    if (sqlException != null) {
      return "type=%s, sqlState=%s, vendorCode=%d"
          .formatted(type, sqlException.getSQLState(), sqlException.getErrorCode());
    }
    return "type=" + type;
  }

  private SQLException findSqlException(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sqlException) {
        return sqlException;
      }
    }
    return null;
  }

  private record ChargeOutcome(PointChargeResult result, Throwable failure) {}
}
