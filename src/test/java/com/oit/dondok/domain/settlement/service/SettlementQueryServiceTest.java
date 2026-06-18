package com.oit.dondok.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.crew.repository.CrewQueryRepository;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.repository.MissionRuleRepository;
import com.oit.dondok.domain.point.entity.PointHistory;
import com.oit.dondok.domain.settlement.dto.response.SettlementDetailResponse;
import com.oit.dondok.domain.settlement.dto.response.SettlementItemDetailResponse;
import com.oit.dondok.domain.settlement.dto.response.SettlementMeResponse;
import com.oit.dondok.domain.settlement.dto.response.SettlementSummaryResponse;
import com.oit.dondok.domain.settlement.entity.ParticipantStatusSnapshot;
import com.oit.dondok.domain.settlement.entity.RemainderPolicy;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementCalculationReason;
import com.oit.dondok.domain.settlement.entity.SettlementFailureCode;
import com.oit.dondok.domain.settlement.entity.SettlementItem;
import com.oit.dondok.domain.settlement.entity.SettlementStatus;
import com.oit.dondok.domain.settlement.repository.SettlementItemRepository;
import com.oit.dondok.domain.settlement.repository.SettlementMeProjection;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementQueryServiceTest {

  private static final Long CREW_ID = 42L;
  private static final Long SETTLEMENT_ID = 500L;
  private static final UUID MEMBER_UUID = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");

  @Mock private SettlementItemRepository settlementItemRepository;
  @Mock private SettlementQueryGuard settlementQueryGuard;
  @Mock private MissionRuleRepository missionRuleRepository;
  @Mock private CrewQueryRepository crewQueryRepository;
  @Mock private CrewParticipantRepository crewParticipantRepository;

  private SettlementQueryService settlementQueryService;

  @BeforeEach
  void setUp() {
    settlementQueryService =
        new SettlementQueryService(
            settlementItemRepository,
            settlementQueryGuard,
            new com.fasterxml.jackson.databind.ObjectMapper(),
            missionRuleRepository,
            crewQueryRepository,
            crewParticipantRepository,
            new CrewMissionStatsCalculator());
  }

  @Test
  void getSettlementSummaryReturnsNoneProjectionWhenSettlementMissing() {
    given(settlementQueryGuard.authorizeSummaryQuery(CREW_ID, MEMBER_UUID))
        .willReturn(Optional.empty());

    SettlementSummaryResponse response =
        settlementQueryService.getSettlementSummary(CREW_ID, MEMBER_UUID);

    assertThat(response)
        .isEqualTo(new SettlementSummaryResponse(CREW_ID, null, "NONE", 0, null, null, null, null));

    then(settlementQueryGuard).should().authorizeSummaryQuery(CREW_ID, MEMBER_UUID);
  }

  @Test
  void getSettlementSummaryThrowsWhenCrewNotFound() {
    given(settlementQueryGuard.authorizeSummaryQuery(CREW_ID, MEMBER_UUID))
        .willThrow(new CustomException(CrewErrorCode.CREW_NOT_FOUND));

    assertThatThrownBy(() -> settlementQueryService.getSettlementSummary(CREW_ID, MEMBER_UUID))
        .isInstanceOfSatisfying(
            CustomException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(CrewErrorCode.CREW_NOT_FOUND));

    then(settlementQueryGuard).should().authorizeSummaryQuery(CREW_ID, MEMBER_UUID);
  }

  @Test
  void getSettlementSummaryThrowsAccessDeniedForOutsideCrew() {
    given(settlementQueryGuard.authorizeSummaryQuery(CREW_ID, MEMBER_UUID))
        .willThrow(new CustomException(CrewErrorCode.CREW_ACCESS_DENIED));

    assertThatThrownBy(() -> settlementQueryService.getSettlementSummary(CREW_ID, MEMBER_UUID))
        .isInstanceOfSatisfying(
            CustomException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(CrewErrorCode.CREW_ACCESS_DENIED));
  }

  @Test
  void getSettlementSummaryMapsPersistedSettlementRow() {
    Settlement settlement =
        settlementSummaryMock(
            CREW_ID,
            501L,
            SettlementStatus.RUNNING,
            1,
            SettlementFailureCode.CALCULATION_FAILED,
            "temporary",
            LocalDateTime.of(2026, 6, 1, 13, 12, 10),
            LocalDateTime.of(2026, 6, 1, 13, 12, 18));

    given(settlementQueryGuard.authorizeSummaryQuery(CREW_ID, MEMBER_UUID))
        .willReturn(Optional.of(settlement));

    SettlementSummaryResponse response =
        settlementQueryService.getSettlementSummary(CREW_ID, MEMBER_UUID);

    assertThat(response.crewId()).isEqualTo(CREW_ID);
    assertThat(response.settlementId()).isEqualTo(501L);
    assertThat(response.status()).isEqualTo("RUNNING");
    assertThat(response.retryCount()).isEqualTo(1);
    assertThat(response.failureCode()).isEqualTo("CALCULATION_FAILED");
    assertThat(response.failureMessage()).isEqualTo("temporary");
    assertThat(response.startedAt()).isEqualTo(OffsetDateTime.parse("2026-06-01T13:12:10+09:00"));
    assertThat(response.finishedAt()).isEqualTo(OffsetDateTime.parse("2026-06-01T13:12:18+09:00"));
  }

  @Test
  void getSettlementDetailThrowsAccessDeniedForOutsideCrew() {
    given(settlementQueryGuard.requireAccessibleSettlement(SETTLEMENT_ID, MEMBER_UUID))
        .willThrow(new CustomException(CrewErrorCode.CREW_ACCESS_DENIED));

    assertThatThrownBy(() -> settlementQueryService.getSettlementDetail(SETTLEMENT_ID, MEMBER_UUID))
        .isInstanceOfSatisfying(
            CustomException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(CrewErrorCode.CREW_ACCESS_DENIED));

    then(settlementQueryGuard).should().requireAccessibleSettlement(SETTLEMENT_ID, MEMBER_UUID);
  }

  @Test
  void getSettlementDetailMapsPersistedSourceValuesAndFormatsShareRatio() {
    Settlement settlement =
        settlementDetailMock(
            CREW_ID,
            SETTLEMENT_ID,
            SettlementStatus.SUCCEEDED,
            1,
            null,
            null,
            LocalDateTime.of(2026, 6, 1, 13, 12, 10),
            LocalDateTime.of(2026, 6, 1, 13, 12, 18),
            5,
            500_000L,
            390,
            499_996L,
            4L,
            RemainderPolicy.HOST_REMAINDER);

    SettlementItem settlementItem =
        settlementItemMock(
            7001L,
            ParticipantStatusSnapshot.LOCKED,
            100_000L,
            BigDecimal.valueOf(0.2),
            4L,
            100_004L,
            11L,
            "{\"included_dates\":[\"2026-05-01\"]}");

    given(settlementQueryGuard.requireAccessibleSettlement(SETTLEMENT_ID, MEMBER_UUID))
        .willReturn(settlement);
    given(settlementItemRepository.findBySettlementIdOrderByIdAsc(SETTLEMENT_ID))
        .willReturn(List.of(settlementItem));
    given(missionRuleRepository.findByCrewId(CREW_ID))
        .willReturn(Optional.of(dailyMissionRuleMock()));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, MEMBER_UUID))
        .willReturn(Optional.of(participantMock(101L)));

    SettlementDetailResponse response =
        settlementQueryService.getSettlementDetail(SETTLEMENT_ID, MEMBER_UUID);

    assertThat(response.totalLockedAmount()).isEqualTo(500_000L);
    assertThat(response.totalRemainderAmount()).isEqualTo(4L);
    assertThat(response.items()).hasSize(1);

    SettlementItemDetailResponse item = response.items().get(0);
    assertThat(item.settlementItemId()).isEqualTo(7001L);
    assertThat(item.baseRefundAmount()).isEqualTo(100_000L);
    assertThat(item.remainderBonusAmount()).isEqualTo(4L);
    assertThat(item.refundAmount()).isEqualTo(100_004L);
    assertThat(item.pointHistoryId()).isEqualTo(11L);
    assertThat(item.shareRatio()).isEqualTo("0.200000");

    then(settlementQueryGuard).should().requireAccessibleSettlement(SETTLEMENT_ID, MEMBER_UUID);
  }

  // 순위(공동순위)·is_me·my_rank·crew_name·기간·mission_days·crew_success_rate 산출
  @Test
  void getSettlementDetailComputesRankIsMeMyRankAndCrewStats() {
    Settlement settlement =
        settlementDetailMock(
            CREW_ID,
            SETTLEMENT_ID,
            SettlementStatus.SUCCEEDED,
            1,
            null,
            null,
            LocalDateTime.of(2026, 6, 1, 13, 12, 10),
            LocalDateTime.of(2026, 6, 1, 13, 12, 18),
            3,
            300_000L,
            60,
            300_000L,
            0L,
            RemainderPolicy.HOST_REMAINDER);

    // share_ratio: 0.5 / 0.3 / 0.3 → 순위 1, 2, 2 (동률 공동순위, cp_id asc)
    SettlementItem item1 = rankableItemMock(7001L, 101L, "일등", "0.500000");
    SettlementItem item2 = rankableItemMock(7002L, 102L, "공동이등A", "0.300000");
    SettlementItem item3 = rankableItemMock(7003L, 103L, "공동이등B", "0.300000");

    given(settlementQueryGuard.requireAccessibleSettlement(SETTLEMENT_ID, MEMBER_UUID))
        .willReturn(settlement);
    given(settlementItemRepository.findBySettlementIdOrderByIdAsc(SETTLEMENT_ID))
        .willReturn(List.of(item1, item2, item3));
    given(missionRuleRepository.findByCrewId(CREW_ID))
        .willReturn(Optional.of(dailyMissionRuleMock()));
    // 뷰어는 cp102 (공동 2등)
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, MEMBER_UUID))
        .willReturn(Optional.of(participantMock(102L)));

    SettlementDetailResponse response =
        settlementQueryService.getSettlementDetail(SETTLEMENT_ID, MEMBER_UUID);

    // crew 통계 (mock crew: 2026-05-01 ~ 2026-05-30 = 30일, DAILY)
    assertThat(response.crewName()).isEqualTo("테스트 크루");
    assertThat(response.crewStartedAt()).isEqualTo(LocalDate.of(2026, 5, 1));
    assertThat(response.crewEndedAt()).isEqualTo(LocalDate.of(2026, 5, 30));
    assertThat(response.missionDays()).isEqualTo(30);
    // 60 / (3 × 30) = 0.6667
    assertThat(response.crewSuccessRate()).isEqualTo("0.6667");

    // items는 id asc 순서: cp101, cp102, cp103
    assertThat(response.items())
        .extracting(SettlementItemDetailResponse::rank)
        .containsExactly(1, 2, 2);
    assertThat(response.items())
        .extracting(SettlementItemDetailResponse::isMe)
        .containsExactly(false, true, false);
    assertThat(response.items())
        .extracting(SettlementItemDetailResponse::nickname)
        .containsExactly("일등", "공동이등A", "공동이등B");
    assertThat(response.myRank()).isEqualTo(2);
  }

  @Test
  void getSettlementMeMapsMyItemFromDedicatedLookup() {
    SettlementMeProjection projection =
        new SettlementMeProjection(
            SETTLEMENT_ID,
            CREW_ID,
            SettlementStatus.SUCCEEDED,
            1,
            null,
            null,
            LocalDateTime.of(2026, 6, 1, 13, 12, 10),
            LocalDateTime.of(2026, 6, 1, 13, 12, 18),
            7002L,
            101L,
            ParticipantStatusSnapshot.LOCKED,
            100_000L,
            5,
            5,
            5,
            0,
            new BigDecimal("0.600000"),
            120_000L,
            1L,
            120_001L,
            12L,
            "{\"included_dates\":[\"2026-05-02\"],\"excluded_logs\":[]}");
    given(settlementQueryGuard.requireAccessibleSettlementMe(SETTLEMENT_ID, MEMBER_UUID))
        .willReturn(projection);

    SettlementMeResponse response =
        settlementQueryService.getSettlementMe(SETTLEMENT_ID, MEMBER_UUID);

    assertThat(response.settlementId()).isEqualTo(SETTLEMENT_ID);
    assertThat(response.crewId()).isEqualTo(CREW_ID);
    assertThat(response.status()).isEqualTo("SUCCEEDED");
    assertThat(response.myItem()).isNotNull();
    assertThat(response.myItem().settlementItemId()).isEqualTo(7002L);
    assertThat(response.myItem().refundAmount()).isEqualTo(120_001L);
    assertThat(response.myItem().shareRatio()).isEqualTo("0.600000");
    assertThat(response.myItem().isMe()).isTrue();
    assertThat(response.myItem().calculationReason().path("included_dates").get(0).asText())
        .isEqualTo("2026-05-02");

    then(settlementQueryGuard).should().requireAccessibleSettlementMe(SETTLEMENT_ID, MEMBER_UUID);
  }

  @Test
  void getSettlementMeReturnsNullMyItemWhenDedicatedLookupIsEmpty() {
    SettlementMeProjection projection =
        new SettlementMeProjection(
            SETTLEMENT_ID,
            CREW_ID,
            SettlementStatus.SUCCEEDED,
            1,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    given(settlementQueryGuard.requireAccessibleSettlementMe(SETTLEMENT_ID, MEMBER_UUID))
        .willReturn(projection);

    SettlementMeResponse response =
        settlementQueryService.getSettlementMe(SETTLEMENT_ID, MEMBER_UUID);

    assertThat(response.myItem()).isNull();
    then(settlementQueryGuard).should().requireAccessibleSettlementMe(SETTLEMENT_ID, MEMBER_UUID);
  }

  @Test
  void getSettlementMeThrowsWhenCalculationReasonMalformed() {
    SettlementMeProjection projection =
        new SettlementMeProjection(
            SETTLEMENT_ID,
            CREW_ID,
            SettlementStatus.SUCCEEDED,
            1,
            null,
            null,
            null,
            null,
            7002L,
            101L,
            ParticipantStatusSnapshot.LOCKED,
            100_000L,
            5,
            5,
            5,
            0,
            new BigDecimal("0.600000"),
            120_000L,
            1L,
            120_001L,
            12L,
            "{invalid}");

    given(settlementQueryGuard.requireAccessibleSettlementMe(SETTLEMENT_ID, MEMBER_UUID))
        .willReturn(projection);

    assertThatThrownBy(() -> settlementQueryService.getSettlementMe(SETTLEMENT_ID, MEMBER_UUID))
        .isInstanceOfSatisfying(
            CustomException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(GlobalErrorCode.SERVER_ERROR));
  }

  @Test
  void getSettlementMeThrowsWhenCalculationReasonIsNotObject() {
    SettlementMeProjection projection =
        new SettlementMeProjection(
            SETTLEMENT_ID,
            CREW_ID,
            SettlementStatus.SUCCEEDED,
            1,
            null,
            null,
            null,
            null,
            7002L,
            101L,
            ParticipantStatusSnapshot.LOCKED,
            100_000L,
            5,
            5,
            5,
            0,
            new BigDecimal("0.600000"),
            120_000L,
            1L,
            120_001L,
            12L,
            "[\"included_dates\",2026]");

    given(settlementQueryGuard.requireAccessibleSettlementMe(SETTLEMENT_ID, MEMBER_UUID))
        .willReturn(projection);

    assertThatThrownBy(() -> settlementQueryService.getSettlementMe(SETTLEMENT_ID, MEMBER_UUID))
        .isInstanceOfSatisfying(
            CustomException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(GlobalErrorCode.SERVER_ERROR));
  }

  @Test
  void getSettlementDetailReturnsCalculationReasonAsJson() {
    Settlement settlement =
        settlementDetailMock(
            CREW_ID,
            SETTLEMENT_ID,
            SettlementStatus.SUCCEEDED,
            1,
            null,
            null,
            null,
            null,
            1,
            100_000L,
            10,
            100_000L,
            0L,
            RemainderPolicy.HOST_REMAINDER);

    given(settlementQueryGuard.requireAccessibleSettlement(SETTLEMENT_ID, MEMBER_UUID))
        .willReturn(settlement);
    given(settlementItemRepository.findBySettlementIdOrderByIdAsc(SETTLEMENT_ID))
        .willReturn(
            List.of(
                settlementItemMock(
                    7001L,
                    ParticipantStatusSnapshot.LOCKED,
                    100_000L,
                    BigDecimal.ZERO,
                    0L,
                    100_000L,
                    null,
                    "{\"participant_key\":1,\"recognized_success_count\":1}")));
    given(missionRuleRepository.findByCrewId(CREW_ID))
        .willReturn(Optional.of(dailyMissionRuleMock()));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, MEMBER_UUID))
        .willReturn(Optional.of(participantMock(101L)));

    SettlementDetailResponse response =
        settlementQueryService.getSettlementDetail(SETTLEMENT_ID, MEMBER_UUID);

    assertThat(response.items().get(0).calculationReason().isObject()).isTrue();
    assertThat(response.items().get(0).calculationReason().path("participant_key").asText())
        .isEqualTo("1");

    then(settlementQueryGuard).should().requireAccessibleSettlement(SETTLEMENT_ID, MEMBER_UUID);
  }

  @Test
  void getSettlementDetailThrowsWhenCalculationReasonMalformed() {
    Settlement settlement =
        settlementDetailMock(
            CREW_ID,
            SETTLEMENT_ID,
            SettlementStatus.SUCCEEDED,
            1,
            null,
            null,
            null,
            null,
            1,
            100_000L,
            10,
            100_000L,
            0L,
            RemainderPolicy.HOST_REMAINDER);

    given(settlementQueryGuard.requireAccessibleSettlement(SETTLEMENT_ID, MEMBER_UUID))
        .willReturn(settlement);
    given(settlementItemRepository.findBySettlementIdOrderByIdAsc(SETTLEMENT_ID))
        .willReturn(
            List.of(
                settlementItemMock(
                    7001L,
                    ParticipantStatusSnapshot.LOCKED,
                    100_000L,
                    BigDecimal.ZERO,
                    0L,
                    100_000L,
                    null,
                    "{invalid}")));

    assertThatThrownBy(() -> settlementQueryService.getSettlementDetail(SETTLEMENT_ID, MEMBER_UUID))
        .isInstanceOfSatisfying(
            CustomException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(GlobalErrorCode.SERVER_ERROR));

    then(settlementQueryGuard).should().requireAccessibleSettlement(SETTLEMENT_ID, MEMBER_UUID);
  }

  @Test
  void getSettlementDetailThrowsWhenCalculationReasonIsNotObject() {
    Settlement settlement =
        settlementDetailMock(
            CREW_ID,
            SETTLEMENT_ID,
            SettlementStatus.SUCCEEDED,
            1,
            null,
            null,
            null,
            null,
            1,
            100_000L,
            10,
            100_000L,
            0L,
            RemainderPolicy.HOST_REMAINDER);

    given(settlementQueryGuard.requireAccessibleSettlement(SETTLEMENT_ID, MEMBER_UUID))
        .willReturn(settlement);
    given(settlementItemRepository.findBySettlementIdOrderByIdAsc(SETTLEMENT_ID))
        .willReturn(
            List.of(
                settlementItemMock(
                    7001L,
                    ParticipantStatusSnapshot.LOCKED,
                    100_000L,
                    BigDecimal.ZERO,
                    0L,
                    100_000L,
                    null,
                    "[\"included_dates\",2026]")));

    assertThatThrownBy(() -> settlementQueryService.getSettlementDetail(SETTLEMENT_ID, MEMBER_UUID))
        .isInstanceOfSatisfying(
            CustomException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(GlobalErrorCode.SERVER_ERROR));

    then(settlementQueryGuard).should().requireAccessibleSettlement(SETTLEMENT_ID, MEMBER_UUID);
  }

  @Test
  void getSettlementDetailThrowsWhenCalculationReasonIsEmptyString() {
    Settlement settlement =
        settlementDetailMock(
            CREW_ID,
            SETTLEMENT_ID,
            SettlementStatus.SUCCEEDED,
            1,
            null,
            null,
            null,
            null,
            1,
            100_000L,
            10,
            100_000L,
            0L,
            RemainderPolicy.HOST_REMAINDER);

    given(settlementQueryGuard.requireAccessibleSettlement(SETTLEMENT_ID, MEMBER_UUID))
        .willReturn(settlement);
    given(settlementItemRepository.findBySettlementIdOrderByIdAsc(SETTLEMENT_ID))
        .willReturn(
            List.of(
                settlementItemMock(
                    7001L,
                    ParticipantStatusSnapshot.LOCKED,
                    100_000L,
                    BigDecimal.ZERO,
                    0L,
                    100_000L,
                    null,
                    "")));

    assertThatThrownBy(() -> settlementQueryService.getSettlementDetail(SETTLEMENT_ID, MEMBER_UUID))
        .isInstanceOfSatisfying(
            CustomException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(GlobalErrorCode.SERVER_ERROR));

    then(settlementQueryGuard).should().requireAccessibleSettlement(SETTLEMENT_ID, MEMBER_UUID);
  }

  @Test
  void getSettlementDetailFormatsShareRatioScaleSix() {
    Settlement settlement =
        settlementDetailMock(
            CREW_ID,
            SETTLEMENT_ID,
            SettlementStatus.SUCCEEDED,
            1,
            null,
            null,
            LocalDateTime.of(2026, 6, 1, 13, 12, 10),
            LocalDateTime.of(2026, 6, 1, 13, 12, 18),
            3,
            1000L,
            12,
            999L,
            1L,
            RemainderPolicy.HOST_REMAINDER);

    SettlementItem item0 = rankableItemMock(7001L, 101L, "회원1", "0.000000");
    SettlementItem item1 = rankableItemMock(7002L, 102L, "회원2", "0.333333");
    SettlementItem item2 = rankableItemMock(7003L, 103L, "회원3", "0.100000");

    given(settlementQueryGuard.requireAccessibleSettlement(SETTLEMENT_ID, MEMBER_UUID))
        .willReturn(settlement);
    given(settlementItemRepository.findBySettlementIdOrderByIdAsc(SETTLEMENT_ID))
        .willReturn(List.of(item0, item1, item2));
    given(missionRuleRepository.findByCrewId(CREW_ID))
        .willReturn(Optional.of(dailyMissionRuleMock()));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, MEMBER_UUID))
        .willReturn(Optional.of(participantMock(101L)));

    SettlementDetailResponse response =
        settlementQueryService.getSettlementDetail(SETTLEMENT_ID, MEMBER_UUID);

    assertThat(response.items())
        .extracting(SettlementItemDetailResponse::shareRatio)
        .containsExactly("0.000000", "0.333333", "0.100000");

    then(settlementQueryGuard).should().requireAccessibleSettlement(SETTLEMENT_ID, MEMBER_UUID);
  }

  private Settlement settlementSummaryMock(
      Long crewId,
      Long settlementId,
      SettlementStatus status,
      Integer retryCount,
      SettlementFailureCode failureCode,
      String failureMessage,
      LocalDateTime startedAt,
      LocalDateTime finishedAt) {
    Crew crew =
        mock(
            Crew.class,
            invocation -> {
              if ("getId".equals(invocation.getMethod().getName())) {
                return crewId;
              }
              return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
            });

    return mock(
        Settlement.class,
        invocation -> {
          return switch (invocation.getMethod().getName()) {
            case "getCrew" -> crew;
            case "getId" -> settlementId;
            case "getStatus" -> status;
            case "getRetryCount" -> retryCount;
            case "getFailureCode" -> failureCode;
            case "getFailureMessage" -> failureMessage;
            case "getStartedAt" -> startedAt;
            case "getFinishedAt" -> finishedAt;
            default -> org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
          };
        });
  }

  private Settlement settlementDetailMock(
      Long crewId,
      Long settlementId,
      SettlementStatus status,
      Integer retryCount,
      SettlementFailureCode failureCode,
      String failureMessage,
      LocalDateTime startedAt,
      LocalDateTime finishedAt,
      Integer totalParticipants,
      Long totalLockedAmount,
      Integer totalRecognizedSuccess,
      Long totalBaseRefundAmount,
      Long totalRemainderAmount,
      RemainderPolicy remainderPolicy) {
    Crew crew =
        mock(
            Crew.class,
            invocation ->
                switch (invocation.getMethod().getName()) {
                  case "getId" -> crewId;
                  case "getTitle" -> "테스트 크루";
                  case "getStartAt" -> LocalDateTime.of(2026, 5, 1, 0, 0);
                  case "getEndAt" -> LocalDateTime.of(2026, 5, 30, 0, 0);
                  default -> org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                });

    return mock(
        Settlement.class,
        invocation -> {
          return switch (invocation.getMethod().getName()) {
            case "getCrew" -> crew;
            case "getId" -> settlementId;
            case "getStatus" -> status;
            case "getRetryCount" -> retryCount;
            case "getFailureCode" -> failureCode;
            case "getFailureMessage" -> failureMessage;
            case "getStartedAt" -> startedAt;
            case "getFinishedAt" -> finishedAt;
            case "getTotalParticipants" -> totalParticipants;
            case "getTotalLockedAmount" -> totalLockedAmount;
            case "getTotalRecognizedSuccess" -> totalRecognizedSuccess;
            case "getTotalBaseRefundAmount" -> totalBaseRefundAmount;
            case "getTotalRemainderAmount" -> totalRemainderAmount;
            case "getRemainderPolicy" -> remainderPolicy;
            default -> org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
          };
        });
  }

  private MissionRule dailyMissionRuleMock() {
    return mock(
        MissionRule.class,
        invocation ->
            switch (invocation.getMethod().getName()) {
              case "getFrequencyType" -> MissionFrequencyType.DAILY;
              case "getId" -> 1L;
              default -> org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
            });
  }

  private CrewParticipant participantMock(long participantId) {
    return mock(
        CrewParticipant.class,
        invocation ->
            "getId".equals(invocation.getMethod().getName())
                ? participantId
                : org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation));
  }

  private Member memberMock(String nickname) {
    return mock(
        Member.class,
        invocation ->
            "getNickname".equals(invocation.getMethod().getName())
                ? nickname
                : org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation));
  }

  private SettlementItem settlementItemMock(
      Long id,
      ParticipantStatusSnapshot participantStatusSnapshot,
      Long baseRefundAmount,
      BigDecimal shareRatio,
      Long remainderBonusAmount,
      Long refundAmount,
      Long pointHistoryId,
      String calculationReason) {
    SettlementCalculationReason calculationReasonValue = null;
    if (calculationReason != null) {
      try {
        calculationReasonValue = SettlementCalculationReason.parse(calculationReason);
      } catch (IllegalArgumentException ignored) {
        // invalid payload should be validated and fail at service mapping
      }
    }
    SettlementCalculationReason finalCalculationReason = calculationReasonValue;
    CrewParticipant participant = participantMock(101L);
    Member member = memberMock("회원");
    PointHistory pointHistory = null;
    if (pointHistoryId != null) {
      final Long pointHistoryIdValue = pointHistoryId;
      pointHistory =
          mock(
              PointHistory.class,
              invocation -> {
                if ("getId".equals(invocation.getMethod().getName())) {
                  return pointHistoryIdValue;
                }
                return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
              });
    }
    final PointHistory finalPointHistory = pointHistory;

    return mock(
        SettlementItem.class,
        invocation -> {
          return switch (invocation.getMethod().getName()) {
            case "getId" -> id;
            case "getCrewParticipant" -> participant;
            case "getMember" -> member;
            case "getParticipantStatusSnapshot" -> participantStatusSnapshot;
            case "getDepositAmount" -> 100_000L;
            case "getSuccessCountRaw" -> 5;
            case "getRecognizedSuccessCount" -> 5;
            case "getRecognizedDatesCount" -> 5;
            case "getExcludedSuccessCount" -> 0;
            case "getShareRatio" -> shareRatio;
            case "getBaseRefundAmount" -> baseRefundAmount;
            case "getRemainderBonusAmount" -> remainderBonusAmount;
            case "getRefundAmount" -> refundAmount;
            case "getPointHistory" -> finalPointHistory;
            case "getCalculationReason" -> finalCalculationReason;
            default -> org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
          };
        });
  }

  // 순위/닉네임/참여자 식별이 필요한 테스트용 item mock
  private SettlementItem rankableItemMock(
      Long id, long participantId, String nickname, String shareRatio) {
    SettlementCalculationReason reason =
        new SettlementCalculationReason(participantId, 5, shareRatio, "HOST_REMAINDER", Map.of());
    CrewParticipant participant = participantMock(participantId);
    Member member = memberMock(nickname);
    BigDecimal ratio = new BigDecimal(shareRatio);

    return mock(
        SettlementItem.class,
        invocation ->
            switch (invocation.getMethod().getName()) {
              case "getId" -> id;
              case "getCrewParticipant" -> participant;
              case "getMember" -> member;
              case "getParticipantStatusSnapshot" -> ParticipantStatusSnapshot.LOCKED;
              case "getDepositAmount" -> 100_000L;
              case "getSuccessCountRaw" -> 5;
              case "getRecognizedSuccessCount" -> 5;
              case "getRecognizedDatesCount" -> 5;
              case "getExcludedSuccessCount" -> 0;
              case "getShareRatio" -> ratio;
              case "getBaseRefundAmount" -> 100_000L;
              case "getRemainderBonusAmount" -> 0L;
              case "getRefundAmount" -> 100_000L;
              case "getPointHistory" -> null;
              case "getCalculationReason" -> reason;
              default -> org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
            });
  }
}
