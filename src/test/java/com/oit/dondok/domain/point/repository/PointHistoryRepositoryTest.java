package com.oit.dondok.domain.point.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.config.JpaAuditingConfig;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.point.entity.PointHistory;
import com.oit.dondok.domain.point.entity.PointReferenceType;
import com.oit.dondok.domain.point.entity.PointTransactionType;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
@Import(JpaAuditingConfig.class)
class PointHistoryRepositoryTest {

  @Autowired private TestEntityManager entityManager;
  @Autowired private PointHistoryRepository pointHistoryRepository;

  @Test
  void findByIdempotencyKeyReturnsPointHistory() {
    Member member = persistMember("member@example.com", "회원");
    PointHistory history =
        persistPointHistory(
            member,
            10_000L,
            PointTransactionType.POINT_CHARGE,
            PointReferenceType.POINT_CHARGE,
            0L,
            "charge:payment-id");
    entityManager.flush();
    entityManager.clear();

    Optional<PointHistory> found = pointHistoryRepository.findByIdempotencyKey("charge:payment-id");

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(history.getId());
    assertThat(found.get().getIdempotencyKey()).isEqualTo("charge:payment-id");
  }

  @Test
  void findByIdempotencyKeyReturnsEmptyWhenHistoryDoesNotExist() {
    Optional<PointHistory> found = pointHistoryRepository.findByIdempotencyKey("charge:missing");

    assertThat(found).isEmpty();
  }

  @Test
  void countByReferenceTypeAndReferenceIdAndTransactionTypeCountsMatchingRowsOnly() {
    Member member = persistMember("member@example.com", "회원");
    persistPointHistory(
        member,
        10_000L,
        PointTransactionType.CREW_RESERVE_RELEASE,
        PointReferenceType.CREW_PARTICIPANT,
        1L,
        "crew:10:participant:1:reserve-release:1");
    persistPointHistory(
        member,
        10_000L,
        PointTransactionType.CREW_RESERVE_RELEASE,
        PointReferenceType.CREW_PARTICIPANT,
        1L,
        "crew:10:participant:1:reserve-release:2");
    persistPointHistory(
        member,
        -10_000L,
        PointTransactionType.CREW_DEPOSIT_RESERVE,
        PointReferenceType.CREW_PARTICIPANT,
        1L,
        "crew:10:participant:1:reserve:1");
    persistPointHistory(
        member,
        10_000L,
        PointTransactionType.CREW_RESERVE_RELEASE,
        PointReferenceType.CREW_PARTICIPANT,
        2L,
        "crew:10:participant:2:reserve-release:1");
    entityManager.flush();
    entityManager.clear();

    long count =
        pointHistoryRepository.countByReferenceTypeAndReferenceIdAndTransactionType(
            PointReferenceType.CREW_PARTICIPANT, 1L, PointTransactionType.CREW_RESERVE_RELEASE);

    assertThat(count).isEqualTo(2L);
  }

  private Member persistMember(String email, String nickname) {
    return entityManager.persistAndFlush(Member.create(email, "password-hash", nickname));
  }

  private PointHistory persistPointHistory(
      Member member,
      long amount,
      PointTransactionType transactionType,
      PointReferenceType referenceType,
      Long referenceId,
      String idempotencyKey) {
    PointHistory history =
        PointHistory.create(
            member,
            amount,
            10_000L,
            0L,
            0L,
            transactionType,
            referenceType,
            referenceId,
            idempotencyKey);
    return entityManager.persistAndFlush(history);
  }
}
