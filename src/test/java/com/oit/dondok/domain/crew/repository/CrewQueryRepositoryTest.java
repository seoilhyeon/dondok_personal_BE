package com.oit.dondok.domain.crew.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.config.JpaAuditingConfig;
import com.oit.dondok.config.QuerydslConfig;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementRuleContextSnapshot;
import com.oit.dondok.domain.settlement.entity.SettlementStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@ActiveProfiles("test")
@DataJpaTest
@Import({JpaAuditingConfig.class, QuerydslConfig.class, CrewQueryRepository.class})
class CrewQueryRepositoryTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 1, 9, 0);

  @Autowired private TestEntityManager entityManager;
  @Autowired private CrewQueryRepository repository;

  @Test
  @DisplayName("정산 완료(SUCCEEDED) 크루는 제외하고, 정산 없음·정산 진행 중 크루는 포함한다")
  void findMyLockedCrewParticipantsExcludesCrewsWithSucceededSettlement() {
    Member host = persistMember("host@example.com", "호스트");
    Member viewer = persistMember("viewer@example.com", "뷰어");

    Crew liveCrew = persistCrew(host, "진행 중 크루");
    Crew runningSettlementCrew = persistCrew(host, "정산 진행 중 크루");
    Crew succeededCrew = persistCrew(host, "정산 완료 크루");

    persistLockedParticipant(liveCrew, viewer);
    persistLockedParticipant(runningSettlementCrew, viewer);
    persistLockedParticipant(succeededCrew, viewer);

    // 정산 진행 중(RUNNING)은 노출 유지, 완료(SUCCEEDED)만 제외
    persistSettlement(runningSettlementCrew, SettlementStatus.RUNNING);
    persistSettlement(succeededCrew, SettlementStatus.SUCCEEDED);
    entityManager.flush();
    entityManager.clear();

    List<CrewParticipant> result = repository.findMyLockedCrewParticipants(viewer.getUuid());

    assertThat(result)
        .extracting(participant -> participant.getCrew().getId())
        .containsExactly(liveCrew.getId(), runningSettlementCrew.getId());
  }

  @Test
  @DisplayName("LOCKED이 아닌 참여(PENDING 등)는 제외한다")
  void findMyLockedCrewParticipantsExcludesNonLockedParticipants() {
    Member host = persistMember("host2@example.com", "호스트2");
    Member viewer = persistMember("viewer2@example.com", "뷰어2");

    Crew lockedCrew = persistCrew(host, "락 크루");
    Crew pendingCrew = persistCrew(host, "대기 크루");
    persistLockedParticipant(lockedCrew, viewer);
    persistPendingParticipant(pendingCrew, viewer);
    entityManager.flush();
    entityManager.clear();

    List<CrewParticipant> result = repository.findMyLockedCrewParticipants(viewer.getUuid());

    assertThat(result)
        .extracting(participant -> participant.getCrew().getId())
        .containsExactly(lockedCrew.getId());
  }

  private Member persistMember(String email, String nickname) {
    return entityManager.persistAndFlush(Member.create(email, "password-hash", nickname));
  }

  private Crew persistCrew(Member host, String title) {
    return entityManager.persistAndFlush(
        Crew.create(
            host,
            title,
            "크루 설명",
            null,
            "OTHER",
            "{}",
            HostPolicyVersion.HOST_POLICY_V1,
            NOW,
            10_000L,
            2,
            5,
            NOW.plusDays(3),
            NOW.plusDays(4),
            NOW.plusDays(30)));
  }

  private CrewParticipant persistLockedParticipant(Crew crew, Member member) {
    return entityManager.persistAndFlush(
        CrewParticipant.create(crew, member, 10_000L, NOW.plusDays(1)));
  }

  private CrewParticipant persistPendingParticipant(Crew crew, Member member) {
    return entityManager.persistAndFlush(
        CrewParticipant.createPending(crew, member, 10_000L, NOW.plusDays(1)));
  }

  private Settlement persistSettlement(Crew crew, SettlementStatus status) {
    Settlement settlement =
        Settlement.createPending(
            crew,
            "batch-test",
            NOW,
            new SettlementRuleContextSnapshot(DailySettlementType.A, MissionFrequencyType.DAILY));
    ReflectionTestUtils.setField(settlement, "status", status);
    return entityManager.persistAndFlush(settlement);
  }
}
