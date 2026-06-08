package com.oit.dondok.domain.point.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.config.JpaAuditingConfig;
import com.oit.dondok.config.QuerydslConfig;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.point.entity.PointAccount;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@ActiveProfiles("test")
@DataJpaTest
@Import({JpaAuditingConfig.class, QuerydslConfig.class, PointBalanceQueryRepository.class})
class PointBalanceQueryRepositoryTest {

  private static final Long DEPOSIT_IN_RECRUITING = 10_000L;
  private static final Long DEPOSIT_IN_ACTIVE = 20_000L;
  private static final Long DEPOSIT_IN_CLOSED = 30_000L;

  @Autowired private TestEntityManager entityManager;
  @Autowired private PointBalanceQueryRepository pointBalanceQueryRepository;

  @Test
  void findWalletSummaryByMemberUuidReturnsAvailableAndComputedLockedAmounts() {
    Member member = persistMember("member@example.com", "회원");
    persistPointAccount(member, 50_000L, 1_000L, 3_000L);

    Crew recruitingCrew = persistCrew(member, "리쿠르팅 크루", CrewStatus.RECRUITING);
    Crew activeCrew = persistCrew(member, "액티브 크루", CrewStatus.ACTIVE);
    Crew closedCrew = persistCrew(member, "종료 크루", CrewStatus.CLOSED);
    Crew pendingCrew = persistCrew(member, "대기 크루", CrewStatus.RECRUITING);

    persistCrewParticipant(
        member, recruitingCrew, CrewParticipantStatus.LOCKED, DEPOSIT_IN_RECRUITING);
    persistCrewParticipant(member, activeCrew, CrewParticipantStatus.LOCKED, DEPOSIT_IN_ACTIVE);
    persistCrewParticipant(member, closedCrew, CrewParticipantStatus.LOCKED, DEPOSIT_IN_CLOSED);

    // 같은 멤버가 아니거나 LOCKED가 아닌 건은 집계에서 제외되어야 함
    Member otherMember = persistMember("other@example.com", "다른회원");
    persistCrewParticipant(otherMember, closedCrew, CrewParticipantStatus.LOCKED, 99_000L);
    persistCrewParticipant(member, pendingCrew, CrewParticipantStatus.PENDING, 7_000L);

    entityManager.flush();
    entityManager.clear();

    PointBalanceProjection projection =
        pointBalanceQueryRepository.findWalletSummaryByMemberUuid(member.getUuid());

    assertThat(projection).isNotNull();
    assertThat(projection.availableBalance()).isEqualTo(50_000L);
    assertThat(projection.reservedBalance()).isEqualTo(1_000L);
    assertThat(projection.lockedBalance()).isEqualTo(3_000L);
    assertThat(projection.activeLockedAmount())
        .isEqualTo(DEPOSIT_IN_RECRUITING + DEPOSIT_IN_ACTIVE);
    assertThat(projection.settlementPendingAmount()).isEqualTo(DEPOSIT_IN_CLOSED);
    assertThat(projection.updatedAt()).isNotNull();
  }

  @Test
  void findWalletSummaryByMemberUuidReturnsNullWhenAccountNotFound() {
    UUID randomUuid = UUID.randomUUID();

    PointBalanceProjection projection =
        pointBalanceQueryRepository.findWalletSummaryByMemberUuid(randomUuid);

    assertThat(projection).isNull();
  }

  private Member persistMember(String email, String nickname) {
    return entityManager.persistAndFlush(Member.create(email, "password-hash", nickname));
  }

  private void persistPointAccount(Member member, long available, long reserved, long locked) {
    PointAccount account = PointAccount.create(member);
    ReflectionTestUtils.setField(account, "availableBalance", available);
    ReflectionTestUtils.setField(account, "reservedBalance", reserved);
    ReflectionTestUtils.setField(account, "lockedBalance", locked);
    entityManager.persistAndFlush(account);
  }

  private Crew persistCrew(Member host, String title, CrewStatus status) {
    Crew crew =
        Crew.create(
            host,
            title,
            title + " 설명",
            null,
            "OTHER",
            "{\"version\":1}",
            HostPolicyVersion.HOST_POLICY_V1,
            LocalDateTime.of(2026, 6, 1, 12, 0),
            5_000L,
            2,
            10,
            LocalDateTime.of(2026, 6, 8, 20, 0),
            LocalDateTime.of(2026, 6, 12, 0, 0),
            LocalDateTime.of(2026, 7, 12, 23, 59));
    ReflectionTestUtils.setField(crew, "status", status);
    return entityManager.persistAndFlush(crew);
  }

  private void persistCrewParticipant(
      Member member, Crew crew, CrewParticipantStatus status, Long depositAmount) {
    CrewParticipant participant;
    if (status == CrewParticipantStatus.PENDING) {
      participant = CrewParticipant.createPending(crew, member, depositAmount, LocalDateTime.now());
    } else {
      participant = CrewParticipant.create(crew, member, depositAmount, LocalDateTime.now());
      ReflectionTestUtils.setField(participant, "status", status);
    }
    entityManager.persistAndFlush(participant);
  }
}
