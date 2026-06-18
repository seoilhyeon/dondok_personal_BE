package com.oit.dondok.domain.settlement.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.point.entity.PointHistory;
import com.oit.dondok.domain.point.entity.PointReferenceType;
import com.oit.dondok.domain.point.entity.PointTransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SettlementItemTest {

  @Test
  void createAssignsFieldsAndCapturesMemberFromParticipant() {
    Settlement settlement =
        Settlement.createPending(
            buildCrew(), "batch", LocalDateTime.now(), settlementRuleContextSnapshot());
    CrewParticipant participant =
        CrewParticipant.create(buildCrew(), buildMember(), 10_000L, LocalDateTime.now());
    LocalDateTime startAt = LocalDateTime.now();
    LocalDateTime endAt = startAt.plusDays(1);
    SettlementItem item =
        SettlementItem.create(
            settlement,
            participant,
            10_000L,
            5,
            4,
            4,
            0,
            startAt,
            endAt,
            BigDecimal.valueOf(0.6),
            9_000L,
            300L,
            10_000L,
            SettlementCalculationReason.parse("{\"participant_key\":1}"),
            "{}",
            "{}");

    assertThat(item.getSettlement()).isEqualTo(settlement);
    assertThat(item.getCrewParticipant()).isEqualTo(participant);
    assertThat(item.getMember()).isEqualTo(participant.getMember());
    assertThat(item.getNickname()).isEqualTo("닉네임");
    assertThat(item.getParticipantStatusSnapshot()).isEqualTo(ParticipantStatusSnapshot.LOCKED);
    assertThat(item.getDepositAmount()).isEqualTo(10_000L);
    assertThat(item.getPeriodStartAt()).isEqualTo(startAt);
    assertThat(item.getPeriodEndAt()).isEqualTo(endAt);
    assertThat(item.getShareRatio()).isEqualByComparingTo(BigDecimal.valueOf(0.6));
    assertThat(item.getCalculationReason().toJson()).isEqualTo("{\"participant_key\":1}");
  }

  @Test
  void createRejectsNulls() {
    Settlement settlement =
        Settlement.createPending(
            buildCrew(), "batch", LocalDateTime.now(), settlementRuleContextSnapshot());
    CrewParticipant participant =
        CrewParticipant.create(buildCrew(), buildMember(), 10_000L, LocalDateTime.now());
    LocalDateTime now = LocalDateTime.now();

    assertThatThrownBy(
            () ->
                SettlementItem.create(
                    null,
                    participant,
                    10_000L,
                    5,
                    4,
                    4,
                    0,
                    now,
                    now.plusHours(1),
                    BigDecimal.ONE,
                    9_000L,
                    300L,
                    10_000L,
                    SettlementCalculationReason.parse("{}"),
                    "{}",
                    "{}"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                SettlementItem.create(
                    settlement,
                    null,
                    10_000L,
                    5,
                    4,
                    4,
                    0,
                    now,
                    now.plusHours(1),
                    BigDecimal.ONE,
                    9_000L,
                    300L,
                    10_000L,
                    SettlementCalculationReason.parse("{}"),
                    "{}",
                    "{}"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void matchesCalculationReturnsWhetherValuesAreEqual() {
    SettlementItem item = buildItem();
    LocalDateTime startAt = item.getPeriodStartAt();
    LocalDateTime endAt = item.getPeriodEndAt();

    assertThat(
            item.matchesCalculation(
                10_000L,
                5,
                4,
                4,
                0,
                startAt,
                endAt,
                BigDecimal.valueOf(0.6),
                9_000L,
                300L,
                10_000L))
        .isTrue();
    assertThat(
            item.matchesCalculation(
                10_001L,
                5,
                4,
                4,
                0,
                startAt,
                endAt,
                BigDecimal.valueOf(0.6),
                9_000L,
                300L,
                10_000L))
        .isFalse();
  }

  @Test
  void linkPointHistoryCanOnlyBeDoneOnce() {
    SettlementItem item = buildItem();
    PointHistory history =
        PointHistory.create(
            buildMember(),
            10_000L,
            0L,
            0L,
            0L,
            PointTransactionType.CREW_SETTLEMENT_REFUND,
            PointReferenceType.SETTLEMENT_ITEM,
            1L,
            "crew:1:participant:1:settlement-refund:final");

    item.linkPointHistory(history);

    assertThat(item.getPointHistory()).isEqualTo(history);

    assertThatThrownBy(
            () ->
                item.linkPointHistory(
                    PointHistory.create(
                        buildMember(),
                        10_000L,
                        0L,
                        0L,
                        0L,
                        PointTransactionType.CREW_SETTLEMENT_REFUND,
                        PointReferenceType.SETTLEMENT_ITEM,
                        2L,
                        "crew:1:participant:2:settlement-refund:final")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void linkPointHistoryRejectsNull() {
    SettlementItem item = buildItem();

    assertThatThrownBy(() -> item.linkPointHistory(null)).isInstanceOf(NullPointerException.class);
  }

  private SettlementItem buildItem() {
    Settlement settlement =
        Settlement.createPending(
            buildCrew(), "batch", LocalDateTime.now(), settlementRuleContextSnapshot());
    CrewParticipant participant =
        CrewParticipant.create(buildCrew(), buildMember(), 10_000L, LocalDateTime.now());
    LocalDateTime now = LocalDateTime.now();
    return SettlementItem.create(
        settlement,
        participant,
        10_000L,
        5,
        4,
        4,
        0,
        now,
        now.plusDays(1),
        BigDecimal.valueOf(0.6),
        9_000L,
        300L,
        10_000L,
        SettlementCalculationReason.parse("{\"participant_key\":1}"),
        "{}",
        "{}");
  }

  private SettlementRuleContextSnapshot settlementRuleContextSnapshot() {
    return new SettlementRuleContextSnapshot(DailySettlementType.B, MissionFrequencyType.DAILY);
  }

  private Crew buildCrew() {
    return Crew.create(
        buildMember(),
        "제목",
        "설명",
        null,
        "EXERCISE",
        "{\"host\":true}",
        HostPolicyVersion.HOST_POLICY_V1,
        LocalDateTime.now(),
        10_000L,
        2,
        3,
        LocalDateTime.now().plusDays(1),
        LocalDateTime.now().plusDays(2),
        LocalDateTime.now().plusDays(30));
  }

  private Member buildMember() {
    return Member.create("member@example.com", "pw", "닉네임");
  }
}
