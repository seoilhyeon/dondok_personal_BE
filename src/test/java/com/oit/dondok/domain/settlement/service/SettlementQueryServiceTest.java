package com.oit.dondok.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
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

  private SettlementQueryService settlementQueryService;

  @BeforeEach
  void setUp() {
    settlementQueryService =
        new SettlementQueryService(
            settlementItemRepository,
            settlementQueryGuard,
            new com.fasterxml.jackson.databind.ObjectMapper());
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

    SettlementItem item0 =
        settlementItemMock(
            7001L,
            ParticipantStatusSnapshot.LOCKED,
            100_000L,
            BigDecimal.ZERO,
            0L,
            100_000L,
            11L,
            "{\"included_dates\":[\"2026-05-01\"]}");
    SettlementItem item1 =
        settlementItemMock(
            7002L,
            ParticipantStatusSnapshot.LOCKED,
            100_000L,
            new BigDecimal("0.333333"),
            0L,
            100_000L,
            12L,
            "{\"included_dates\":[\"2026-05-02\"]}");
    SettlementItem item2 =
        settlementItemMock(
            7003L,
            ParticipantStatusSnapshot.LOCKED,
            100_000L,
            new BigDecimal("0.1"),
            0L,
            100_000L,
            13L,
            "{\"included_dates\":[\"2026-05-03\"]}");

    given(settlementQueryGuard.requireAccessibleSettlement(SETTLEMENT_ID, MEMBER_UUID))
        .willReturn(settlement);
    given(settlementItemRepository.findBySettlementIdOrderByIdAsc(SETTLEMENT_ID))
        .willReturn(List.of(item0, item1, item2));

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
    CrewParticipant participant =
        mock(
            CrewParticipant.class,
            invocation -> {
              if ("getId".equals(invocation.getMethod().getName())) {
                return 101L;
              }
              return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
            });
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
}
