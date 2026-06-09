package com.oit.dondok.domain.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

import com.oit.dondok.domain.point.dto.response.PointHistoryListResponse;
import com.oit.dondok.domain.point.dto.response.PointReferenceMetaResponse;
import com.oit.dondok.domain.point.entity.PointReferenceType;
import com.oit.dondok.domain.point.entity.PointTransactionType;
import com.oit.dondok.domain.point.exception.PointErrorCode;
import com.oit.dondok.domain.point.repository.PointBalanceProjection;
import com.oit.dondok.domain.point.repository.PointBalanceQueryRepository;
import com.oit.dondok.domain.point.repository.PointHistoryItemProjection;
import com.oit.dondok.domain.point.repository.PointHistoryQueryRepository;
import com.oit.dondok.domain.point.repository.PointHistoryReferenceMetaProjection;
import com.oit.dondok.global.exception.CustomException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PointQueryServiceTest {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

  @Mock private PointBalanceQueryRepository pointBalanceQueryRepository;
  @Mock private PointHistoryQueryRepository pointHistoryQueryRepository;

  @InjectMocks private PointQueryService pointQueryService;

  @Test
  void findBalanceReturnsTotalAndComputedWalletProjection() {
    UUID memberUuid = UUID.randomUUID();
    given(pointBalanceQueryRepository.findWalletSummaryByMemberUuid(memberUuid))
        .willReturn(
            new PointBalanceProjection(
                10_000L,
                2_000L,
                5_000L,
                3_000L,
                700L,
                1_000L,
                LocalDateTime.of(2026, 6, 8, 10, 0)));

    var response = pointQueryService.findBalance(memberUuid);

    assertThat(response.availableBalance()).isEqualTo(10_000L);
    assertThat(response.reservedBalance()).isEqualTo(2_000L);
    assertThat(response.activeLockedAmount()).isEqualTo(5_000L);
    assertThat(response.settlementPendingAmount()).isEqualTo(3_000L);
    assertThat(response.settlementFailedAmount()).isEqualTo(700L);
    assertThat(response.lockedBalance()).isEqualTo(1_000L);
    assertThat(response.totalBalance()).isEqualTo(13_000L);
    assertThat(response.updatedAt())
        .isEqualTo(LocalDateTime.of(2026, 6, 8, 10, 0).atZone(SEOUL_ZONE).toOffsetDateTime());
  }

  @Test
  void findBalanceThrowsWhenAccountNotFound() {
    UUID memberUuid = UUID.randomUUID();
    given(pointBalanceQueryRepository.findWalletSummaryByMemberUuid(memberUuid)).willReturn(null);

    assertThatThrownBy(() -> pointQueryService.findBalance(memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(PointErrorCode.POINT_ACCOUNT_NOT_FOUND);
  }

  @Test
  void findHistoriesUsesDefaultLimitAndReturnsNextCursorWhenMoreRowsExist() {
    UUID memberUuid = UUID.randomUUID();
    LocalDateTime created1 = LocalDateTime.of(2026, 6, 8, 10, 0);
    LocalDateTime created2 = LocalDateTime.of(2026, 6, 8, 9, 0);
    LocalDateTime created3 = LocalDateTime.of(2026, 6, 8, 8, 0);

    List<PointHistoryItemProjection> rows =
        List.of(
            new PointHistoryItemProjection(
                3L,
                1_000L,
                9_000L,
                PointTransactionType.POINT_CHARGE,
                PointReferenceType.POINT_CHARGE,
                0L,
                created1),
            new PointHistoryItemProjection(
                2L,
                10_000L,
                10_000L,
                PointTransactionType.CREW_DEPOSIT_RESERVE,
                PointReferenceType.CREW_PARTICIPANT,
                10L,
                created2),
            new PointHistoryItemProjection(
                1L,
                7_000L,
                17_000L,
                PointTransactionType.CREW_SETTLEMENT_REFUND,
                PointReferenceType.SETTLEMENT_ITEM,
                20L,
                created3));

    given(
            pointHistoryQueryRepository.findHistoriesByCursor(
                memberUuid, 21, null, null, null, null, null))
        .willReturn(rows);
    given(pointHistoryQueryRepository.findCrewParticipantReferenceMeta(memberUuid, Set.of(10L)))
        .willReturn(Map.of(10L, new PointHistoryReferenceMetaProjection(10L, 100L, "크루A")));
    given(pointHistoryQueryRepository.findSettlementItemReferenceMeta(memberUuid, Set.of(20L)))
        .willReturn(Map.of(20L, new PointHistoryReferenceMetaProjection(20L, 200L, "크루B")));

    var response = pointQueryService.findHistories(memberUuid, null, null);

    assertThat(response.items()).hasSize(3);
    assertThat(response.nextCursor()).isNull();
    assertThat(response.items().get(0).referenceMeta()).isNull();
    assertThat(response.items().get(1).referenceMeta())
        .isEqualTo(new PointReferenceMetaResponse(100L, "크루A"));
    assertThat(response.items().get(2).referenceMeta())
        .isEqualTo(new PointReferenceMetaResponse(200L, "크루B"));
  }

  @Test
  void findHistoriesSupportsCursorPagination() {
    UUID memberUuid = UUID.randomUUID();
    OffsetDateTime cursorTime =
        LocalDateTime.of(2026, 6, 8, 10, 0).atZone(SEOUL_ZONE).toOffsetDateTime();
    String cursor = encodeCursor(cursorTime, 3L);
    LocalDateTime cursorCreatedAt = cursorTime.toLocalDateTime();

    List<PointHistoryItemProjection> rows =
        List.of(
            new PointHistoryItemProjection(
                3L,
                1_000L,
                1_000L,
                PointTransactionType.POINT_CHARGE,
                PointReferenceType.POINT_CHARGE,
                0L,
                LocalDateTime.of(2026, 6, 8, 9, 0)));

    given(
            pointHistoryQueryRepository.findHistoriesByCursor(
                memberUuid, 6, cursorCreatedAt, 3L, null, null, null))
        .willReturn(rows);

    PointHistoryListResponse response = pointQueryService.findHistories(memberUuid, 5, cursor);

    assertThat(response.items()).hasSize(1);
    assertThat(response.nextCursor()).isNull();
    then(pointHistoryQueryRepository)
        .should()
        .findHistoriesByCursor(memberUuid, 6, cursorCreatedAt, 3L, null, null, null);
    then(pointHistoryQueryRepository)
        .should(never())
        .findCrewParticipantReferenceMeta(any(), any());
    then(pointHistoryQueryRepository).should(never()).findSettlementItemReferenceMeta(any(), any());
  }

  @Test
  void findHistoriesThrowsWhenLimitIsOutOfRange() {
    UUID memberUuid = UUID.randomUUID();

    assertThatThrownBy(() -> pointQueryService.findHistories(memberUuid, 0, null))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(PointErrorCode.INVALID_LIMIT);
    assertThatThrownBy(() -> pointQueryService.findHistories(memberUuid, 101, null))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(PointErrorCode.INVALID_LIMIT);
  }

  @Test
  void findHistoriesThrowsWhenCursorIsInvalidFormat() {
    UUID memberUuid = UUID.randomUUID();

    assertThatThrownBy(() -> pointQueryService.findHistories(memberUuid, 20, "invalid-cursor"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(PointErrorCode.INVALID_CURSOR);
    assertThatThrownBy(
            () -> pointQueryService.findHistories(memberUuid, 20, "2026-01-01T00:00:00Z_"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(PointErrorCode.INVALID_CURSOR);
  }

  @Test
  void findHistoriesReturnsNextCursorWhenPageOverflowsLimit() {
    UUID memberUuid = UUID.randomUUID();
    LocalDateTime createdAt = LocalDateTime.of(2026, 6, 8, 12, 0);
    List<PointHistoryItemProjection> rows =
        List.of(
            new PointHistoryItemProjection(
                10L,
                1_000L,
                1_000L,
                PointTransactionType.POINT_CHARGE,
                PointReferenceType.POINT_CHARGE,
                null,
                createdAt),
            new PointHistoryItemProjection(
                9L,
                2_000L,
                3_000L,
                PointTransactionType.POINT_CHARGE,
                PointReferenceType.POINT_CHARGE,
                null,
                createdAt.minusHours(1)),
            new PointHistoryItemProjection(
                8L,
                3_000L,
                6_000L,
                PointTransactionType.POINT_CHARGE,
                PointReferenceType.POINT_CHARGE,
                null,
                createdAt.minusHours(2)));

    given(
            pointHistoryQueryRepository.findHistoriesByCursor(
                memberUuid, 3, null, null, null, null, null))
        .willReturn(rows);

    PointHistoryListResponse response = pointQueryService.findHistories(memberUuid, 2, null);

    assertThat(response.items()).hasSize(2);
    assertThat(response.nextCursor()).isNotNull();
    assertThat(response.nextCursor()).isNotBlank();
    assertThat(response.nextCursor()).doesNotContain("_9");
    assertThat(decodeCursor(response.nextCursor())).isEqualTo("v1|2026-06-08T11:00+09:00|9");
  }

  @Test
  void findHistoriesMapsReferenceMetaForCrewParticipantAndSettlementItem() {
    UUID memberUuid = UUID.randomUUID();
    List<PointHistoryItemProjection> rows =
        List.of(
            new PointHistoryItemProjection(
                1L,
                -10_000L,
                1000L,
                PointTransactionType.CREW_DEPOSIT_RESERVE,
                PointReferenceType.CREW_PARTICIPANT,
                11L,
                LocalDateTime.of(2026, 6, 7, 10, 0)),
            new PointHistoryItemProjection(
                2L,
                1_000L,
                2_000L,
                PointTransactionType.CREW_SETTLEMENT_REFUND,
                PointReferenceType.SETTLEMENT_ITEM,
                22L,
                LocalDateTime.of(2026, 6, 7, 9, 0)),
            new PointHistoryItemProjection(
                3L,
                1_000L,
                3_000L,
                PointTransactionType.POINT_CHARGE,
                PointReferenceType.POINT_CHARGE,
                null,
                LocalDateTime.of(2026, 6, 7, 8, 0)));

    given(
            pointHistoryQueryRepository.findHistoriesByCursor(
                memberUuid, 3, null, null, null, null, null))
        .willReturn(rows);
    given(pointHistoryQueryRepository.findCrewParticipantReferenceMeta(memberUuid, Set.of(11L)))
        .willReturn(Map.of(11L, new PointHistoryReferenceMetaProjection(11L, 111L, "참여 크루")));
    given(pointHistoryQueryRepository.findSettlementItemReferenceMeta(memberUuid, Set.of(22L)))
        .willReturn(Map.of(22L, new PointHistoryReferenceMetaProjection(22L, 222L, "정산 크루")));

    PointHistoryListResponse response = pointQueryService.findHistories(memberUuid, 2, null);

    assertThat(response.items()).hasSize(2);
    assertThat(response.items().get(0).referenceMeta())
        .isEqualTo(new PointReferenceMetaResponse(111L, "참여 크루"));
    assertThat(response.items().get(1).referenceMeta())
        .isEqualTo(new PointReferenceMetaResponse(222L, "정산 크루"));
  }

  @Test
  void findHistoriesMapsSettlementTypeFilterToSettlementRefundOnly() {
    UUID memberUuid = UUID.randomUUID();
    List<PointHistoryItemProjection> rows =
        List.of(
            new PointHistoryItemProjection(
                1L,
                1_000L,
                2_000L,
                PointTransactionType.CREW_SETTLEMENT_REFUND,
                PointReferenceType.SETTLEMENT_ITEM,
                11L,
                LocalDateTime.of(2026, 6, 9, 10, 0)));

    given(
            pointHistoryQueryRepository.findHistoriesByCursor(
                memberUuid,
                21,
                null,
                null,
                Set.of(PointTransactionType.CREW_SETTLEMENT_REFUND),
                null,
                null))
        .willReturn(rows);
    given(pointHistoryQueryRepository.findSettlementItemReferenceMeta(memberUuid, Set.of(11L)))
        .willReturn(Map.of(11L, new PointHistoryReferenceMetaProjection(11L, 111L, "정산 크루")));

    PointHistoryListResponse response =
        pointQueryService.findHistories(memberUuid, null, null, "settlement", null);

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().get(0).transactionType())
        .isEqualTo(PointTransactionType.CREW_SETTLEMENT_REFUND);

    then(pointHistoryQueryRepository)
        .should()
        .findHistoriesByCursor(
            memberUuid,
            21,
            null,
            null,
            Set.of(PointTransactionType.CREW_SETTLEMENT_REFUND),
            null,
            null);
  }

  @Test
  void findHistoriesAppliesTypeAndMonthFilters() {
    UUID memberUuid = UUID.randomUUID();
    LocalDateTime monthStart = LocalDateTime.of(2026, 6, 1, 0, 0);
    LocalDateTime monthEnd = LocalDateTime.of(2026, 7, 1, 0, 0);
    List<PointHistoryItemProjection> rows =
        List.of(
            new PointHistoryItemProjection(
                1L,
                -10_000L,
                90_000L,
                PointTransactionType.CREW_DEPOSIT_RESERVE,
                PointReferenceType.CREW_PARTICIPANT,
                11L,
                LocalDateTime.of(2026, 6, 8, 10, 0)));

    given(
            pointHistoryQueryRepository.findHistoriesByCursor(
                memberUuid,
                21,
                null,
                null,
                Set.of(
                    PointTransactionType.CREW_DEPOSIT_RESERVE,
                    PointTransactionType.CREW_DEPOSIT_LOCK),
                monthStart,
                monthEnd))
        .willReturn(rows);
    given(pointHistoryQueryRepository.findCrewParticipantReferenceMeta(memberUuid, Set.of(11L)))
        .willReturn(Map.of(11L, new PointHistoryReferenceMetaProjection(11L, 111L, "예치 크루")));

    PointHistoryListResponse response =
        pointQueryService.findHistories(memberUuid, null, null, "deposit", "2026-06");

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().get(0).transactionType())
        .isEqualTo(PointTransactionType.CREW_DEPOSIT_RESERVE);
    assertThat(response.items().get(0).referenceMeta())
        .isEqualTo(new PointReferenceMetaResponse(111L, "예치 크루"));
    then(pointHistoryQueryRepository)
        .should()
        .findHistoriesByCursor(
            memberUuid,
            21,
            null,
            null,
            Set.of(
                PointTransactionType.CREW_DEPOSIT_RESERVE, PointTransactionType.CREW_DEPOSIT_LOCK),
            monthStart,
            monthEnd);
  }

  @Test
  void findHistoriesReturnsEmptyForUnsupportedWithdrawalFilter() {
    UUID memberUuid = UUID.randomUUID();

    PointHistoryListResponse response =
        pointQueryService.findHistories(memberUuid, 20, null, "withdrawal", null);

    assertThat(response.items()).isEmpty();
    assertThat(response.nextCursor()).isNull();
    verifyNoInteractions(pointHistoryQueryRepository);
  }

  @Test
  void findHistoriesThrowsWhenCursorHasInvalidPadding() {
    UUID memberUuid = UUID.randomUUID();

    assertThatThrownBy(() -> pointQueryService.findHistories(memberUuid, 20, "a"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(PointErrorCode.INVALID_CURSOR);
  }

  @Test
  void findHistoriesThrowsWhenTypeFilterIsInvalid() {
    UUID memberUuid = UUID.randomUUID();

    assertThatThrownBy(() -> pointQueryService.findHistories(memberUuid, 20, null, "unknown", null))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(PointErrorCode.INVALID_HISTORY_TYPE);
  }

  @Test
  void findHistoriesThrowsWhenMonthFilterIsInvalid() {
    UUID memberUuid = UUID.randomUUID();

    assertThatThrownBy(() -> pointQueryService.findHistories(memberUuid, 20, null, null, "2026-13"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(PointErrorCode.INVALID_HISTORY_MONTH);
  }

  private static String encodeCursor(OffsetDateTime createdAt, Long pointHistoryId) {
    String payload = "v1|" + createdAt + "|" + pointHistoryId;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }

  private static String decodeCursor(String cursor) {
    int remainder = cursor.length() % 4;
    String padded = remainder == 0 ? cursor : cursor + "=".repeat(4 - remainder);
    return new String(Base64.getUrlDecoder().decode(padded), StandardCharsets.UTF_8);
  }
}
