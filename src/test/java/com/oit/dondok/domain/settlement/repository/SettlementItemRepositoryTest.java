package com.oit.dondok.domain.settlement.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.config.JpaAuditingConfig;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementCalculationReason;
import com.oit.dondok.domain.settlement.entity.SettlementItem;
import com.oit.dondok.domain.settlement.entity.SettlementRuleContextSnapshot;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest(
    properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(JpaAuditingConfig.class)
class SettlementItemRepositoryTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 13, 0, 0);

  @Autowired private SettlementItemRepository settlementItemRepository;
  @Autowired private TestEntityManager entityManager;

  // settlement_item을 id 오름차순으로, member 닉네임과 함께(entity graph) 반환한다.
  @Test
  void findBySettlementIdOrderByIdAscReturnsItemsWithMemberNicknameInIdOrder() {
    Member host = persistMember("host@example.com", "호스트");
    Member member2 = persistMember("member2@example.com", "회원2");
    Crew crew = persistCrew(host);
    CrewParticipant participant1 = persistParticipant(crew, host);
    CrewParticipant participant2 = persistParticipant(crew, member2);
    Settlement settlement = persistSettlement(crew);

    SettlementItem item1 = persistItem(settlement, participant1, "0.400000", 2000L);
    SettlementItem item2 = persistItem(settlement, participant2, "0.600000", 3000L);
    entityManager.flush();
    entityManager.clear();

    List<SettlementItem> items =
        settlementItemRepository.findBySettlementIdOrderByIdAsc(settlement.getId());

    assertThat(items).extracting(SettlementItem::getId).containsExactly(item1.getId(), item2.getId());
    assertThat(items.get(0).getMember().getNickname()).isEqualTo("호스트");
    assertThat(items.get(0).getCrewParticipant().getId()).isEqualTo(participant1.getId());
    assertThat(items.get(1).getMember().getNickname()).isEqualTo("회원2");
    assertThat(items.get(1).getRefundAmount()).isEqualTo(3000L);
  }

  private Member persistMember(String email, String nickname) {
    return entityManager.persist(Member.create(email, "password-hash", nickname));
  }

  private Crew persistCrew(Member host) {
    return entityManager.persist(
        Crew.create(
            host,
            "크루",
            "크루 설명",
            null,
            "OTHER",
            "{}",
            HostPolicyVersion.HOST_POLICY_V1,
            NOW.minusDays(31),
            10_000L,
            2,
            10,
            NOW.minusDays(30),
            NOW.minusDays(1),
            NOW));
  }

  private CrewParticipant persistParticipant(Crew crew, Member member) {
    return entityManager.persist(
        CrewParticipant.create(crew, member, 10_000L, NOW.minusDays(30)));
  }

  private Settlement persistSettlement(Crew crew) {
    return entityManager.persist(
        Settlement.createPending(
            crew,
            "batch-test",
            NOW,
            new SettlementRuleContextSnapshot(
                DailySettlementType.A, MissionFrequencyType.DAILY)));
  }

  private SettlementItem persistItem(
      Settlement settlement, CrewParticipant participant, String shareRatio, long refundAmount) {
    SettlementCalculationReason reason =
        new SettlementCalculationReason(
            participant.getId(), 5, shareRatio, "HOST_REMAINDER", Map.of());
    return entityManager.persist(
        SettlementItem.create(
            settlement,
            participant,
            10_000L,
            5,
            5,
            5,
            0,
            NOW.minusDays(30),
            NOW.minusDays(1),
            new BigDecimal(shareRatio),
            refundAmount,
            0L,
            refundAmount,
            reason,
            null,
            null));
  }
}
