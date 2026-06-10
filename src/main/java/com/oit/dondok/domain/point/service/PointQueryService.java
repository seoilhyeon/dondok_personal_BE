package com.oit.dondok.domain.point.service;

import com.oit.dondok.domain.point.dto.response.PointBalanceResponse;
import com.oit.dondok.domain.point.dto.response.PointHistoryItemResponse;
import com.oit.dondok.domain.point.dto.response.PointHistoryListResponse;
import com.oit.dondok.domain.point.dto.response.PointReferenceMetaResponse;
import com.oit.dondok.domain.point.dto.response.WalletHistoryItemResponse;
import com.oit.dondok.domain.point.dto.response.WalletHistoryListResponse;
import com.oit.dondok.domain.point.entity.PointReferenceType;
import com.oit.dondok.domain.point.entity.PointTransactionType;
import com.oit.dondok.domain.point.entity.WalletHistoryDisplayType;
import com.oit.dondok.domain.point.exception.PointErrorCode;
import com.oit.dondok.domain.point.repository.PointBalanceProjection;
import com.oit.dondok.domain.point.repository.PointBalanceQueryRepository;
import com.oit.dondok.domain.point.repository.PointHistoryItemProjection;
import com.oit.dondok.domain.point.repository.PointHistoryQueryRepository;
import com.oit.dondok.domain.point.repository.PointHistoryReferenceMetaProjection;
import com.oit.dondok.domain.point.repository.WalletHistoryEventProjection;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.util.SeoulDateTimeUtils;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointQueryService {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 100;
  private static final String CURSOR_VERSION = "v1";
  private static final String CURSOR_DELIMITER = "|";

  private final PointBalanceQueryRepository pointBalanceQueryRepository;
  private final PointHistoryQueryRepository pointHistoryQueryRepository;

  public PointBalanceResponse findBalance(UUID memberUuid) {
    PointBalanceProjection projection =
        pointBalanceQueryRepository.findWalletSummaryByMemberUuid(memberUuid);

    if (projection == null) {
      throw new CustomException(PointErrorCode.POINT_ACCOUNT_NOT_FOUND);
    }

    long available = projection.availableBalance();
    long reserved = projection.reservedBalance();
    long locked = projection.lockedBalance();
    long totalBalance = available + reserved + locked;

    return new PointBalanceResponse(
        available,
        reserved,
        projection.activeLockedAmount(),
        projection.settlementPendingAmount(),
        projection.settlementFailedAmount(),
        locked,
        totalBalance,
        SeoulDateTimeUtils.toSeoulOffset(projection.updatedAt()));
  }

  public PointHistoryListResponse findHistories(
      UUID memberUuid, Integer limitInput, String cursor) {
    return findHistories(memberUuid, limitInput, cursor, null, null);
  }

  public PointHistoryListResponse findHistories(
      UUID memberUuid, Integer limitInput, String cursor, String typeInput, String monthInput) {
    int effectiveLimit = resolveLimit(limitInput);
    Cursor cursorState = parseCursor(cursor);
    PointHistoryTypeFilter typeFilter = parseTypeFilter(typeInput);
    YearMonth month = parseMonth(monthInput);

    Set<PointTransactionType> transactionTypes =
        typeFilter == null ? null : typeFilter.transactionTypes();
    if (transactionTypes != null && transactionTypes.isEmpty()) {
      return new PointHistoryListResponse(List.of(), null);
    }

    LocalDateTime monthStartInclusive = month == null ? null : month.atDay(1).atStartOfDay();
    LocalDateTime monthEndExclusive =
        monthStartInclusive == null ? null : month.plusMonths(1).atDay(1).atStartOfDay();

    int queryLimit = effectiveLimit + 1;
    List<PointHistoryItemProjection> rows =
        pointHistoryQueryRepository.findHistoriesByCursor(
            memberUuid,
            queryLimit,
            cursorState.createdAt(),
            cursorState.pointHistoryId(),
            transactionTypes,
            monthStartInclusive,
            monthEndExclusive);

    boolean hasNext = rows.size() > effectiveLimit;
    List<PointHistoryItemProjection> pageItems = hasNext ? rows.subList(0, effectiveLimit) : rows;

    Map<Long, PointHistoryReferenceMetaProjection> crewMeta =
        resolveReferenceMeta(memberUuid, pageItems, PointReferenceType.CREW_PARTICIPANT);
    Map<Long, PointHistoryReferenceMetaProjection> settlementMeta =
        resolveReferenceMeta(memberUuid, pageItems, PointReferenceType.SETTLEMENT_ITEM);

    List<PointHistoryItemResponse> items =
        pageItems.stream()
            .map(
                item ->
                    new PointHistoryItemResponse(
                        item.pointHistoryId(),
                        item.amount(),
                        item.balanceAfter(),
                        item.transactionType(),
                        item.referenceType(),
                        item.referenceId(),
                        resolveReferenceMeta(
                            item.referenceType(), item.referenceId(), crewMeta, settlementMeta),
                        SeoulDateTimeUtils.toSeoulOffset(item.createdAt())))
            .toList();

    String nextCursor =
        hasNext
            ? toCursor(
                pageItems.get(pageItems.size() - 1).createdAt(),
                pageItems.get(pageItems.size() - 1).pointHistoryId())
            : null;

    return new PointHistoryListResponse(items, nextCursor);
  }

  public WalletHistoryListResponse findWalletHistories(
      UUID memberUuid, Integer limitInput, String cursor, String typeInput, String monthInput) {
    int effectiveLimit = resolveLimit(limitInput);
    WalletCursor cursorState = parseWalletCursor(cursor);
    WalletHistoryTypeFilter typeFilter = parseWalletTypeFilter(typeInput);
    YearMonth month = parseMonth(monthInput);

    Set<WalletHistoryDisplayType> displayTypes =
        typeFilter == null ? null : typeFilter.displayTypes();
    if (displayTypes != null && displayTypes.isEmpty()) {
      return new WalletHistoryListResponse(List.of(), null);
    }

    LocalDateTime monthStartInclusive = month == null ? null : month.atDay(1).atStartOfDay();
    LocalDateTime monthEndExclusive =
        monthStartInclusive == null ? null : month.plusMonths(1).atDay(1).atStartOfDay();

    int queryLimit = effectiveLimit + 1;
    List<WalletHistoryEventProjection> rows =
        pointHistoryQueryRepository.findWalletHistoriesByCursor(
            memberUuid,
            queryLimit,
            cursorState.createdAt(),
            cursorState.walletEventId(),
            displayTypes,
            monthStartInclusive,
            monthEndExclusive);

    boolean hasNext = rows.size() > effectiveLimit;
    List<WalletHistoryEventProjection> pageItems = hasNext ? rows.subList(0, effectiveLimit) : rows;

    Map<Long, PointHistoryReferenceMetaProjection> crewMeta =
        resolveWalletReferenceMeta(memberUuid, pageItems, PointReferenceType.CREW_PARTICIPANT);
    Map<Long, PointHistoryReferenceMetaProjection> settlementMeta =
        resolveWalletReferenceMeta(memberUuid, pageItems, PointReferenceType.SETTLEMENT_ITEM);

    List<WalletHistoryItemResponse> items =
        pageItems.stream()
            .map(
                item ->
                    new WalletHistoryItemResponse(
                        item.walletEventId(),
                        item.amount(),
                        item.balanceAfter(),
                        item.displayType(),
                        item.status(),
                        item.referenceType(),
                        item.referenceId(),
                        resolveReferenceMeta(
                            item.referenceType(), item.referenceId(), crewMeta, settlementMeta),
                        SeoulDateTimeUtils.toSeoulOffset(item.createdAt())))
            .toList();

    String nextCursor =
        hasNext
            ? toWalletCursor(
                pageItems.get(pageItems.size() - 1).createdAt(),
                pageItems.get(pageItems.size() - 1).walletEventId())
            : null;

    return new WalletHistoryListResponse(items, nextCursor);
  }

  private PointHistoryTypeFilter parseTypeFilter(String typeInput) {
    try {
      return PointHistoryTypeFilter.from(typeInput);
    } catch (IllegalArgumentException e) {
      throw new CustomException(PointErrorCode.INVALID_HISTORY_TYPE);
    }
  }

  private WalletHistoryTypeFilter parseWalletTypeFilter(String typeInput) {
    try {
      return WalletHistoryTypeFilter.from(typeInput);
    } catch (IllegalArgumentException e) {
      throw new CustomException(PointErrorCode.INVALID_HISTORY_TYPE);
    }
  }

  private YearMonth parseMonth(String monthInput) {
    if (monthInput == null || monthInput.isBlank()) {
      return null;
    }
    try {
      return YearMonth.parse(monthInput.trim());
    } catch (DateTimeParseException e) {
      throw new CustomException(PointErrorCode.INVALID_HISTORY_MONTH);
    }
  }

  private int resolveLimit(Integer limitInput) {
    int limit = limitInput == null ? DEFAULT_LIMIT : limitInput;
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new CustomException(PointErrorCode.INVALID_LIMIT);
    }
    return limit;
  }

  private Cursor parseCursor(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return new Cursor(null, null);
    }

    try {
      String decoded =
          new String(
              Base64.getUrlDecoder().decode(restoreBase64Padding(cursor.trim())),
              StandardCharsets.UTF_8);
      String[] parts = decoded.split("\\|", -1);
      if (parts.length != 3 || !CURSOR_VERSION.equals(parts[0])) {
        throw new CustomException(PointErrorCode.INVALID_CURSOR);
      }

      OffsetDateTime createdAt = OffsetDateTime.parse(parts[1]);
      Long pointHistoryId = Long.parseLong(parts[2]);
      LocalDateTime createdAtInSeoul = createdAt.atZoneSameInstant(SEOUL_ZONE).toLocalDateTime();
      return new Cursor(createdAtInSeoul, pointHistoryId);
    } catch (DateTimeParseException | IllegalArgumentException e) {
      throw new CustomException(PointErrorCode.INVALID_CURSOR);
    }
  }

  private WalletCursor parseWalletCursor(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return new WalletCursor(null, null);
    }

    try {
      String decoded =
          new String(
              Base64.getUrlDecoder().decode(restoreBase64Padding(cursor.trim())),
              StandardCharsets.UTF_8);
      String[] parts = decoded.split("\\|", -1);
      if (parts.length != 3 || !CURSOR_VERSION.equals(parts[0]) || parts[2].isBlank()) {
        throw new CustomException(PointErrorCode.INVALID_CURSOR);
      }

      OffsetDateTime createdAt = OffsetDateTime.parse(parts[1]);
      LocalDateTime createdAtInSeoul = createdAt.atZoneSameInstant(SEOUL_ZONE).toLocalDateTime();
      return new WalletCursor(createdAtInSeoul, parts[2]);
    } catch (DateTimeParseException | IllegalArgumentException e) {
      throw new CustomException(PointErrorCode.INVALID_CURSOR);
    }
  }

  private String toCursor(LocalDateTime createdAt, Long pointHistoryId) {
    String payload =
        String.join(
            CURSOR_DELIMITER,
            CURSOR_VERSION,
            SeoulDateTimeUtils.toSeoulOffset(createdAt).toString(),
            pointHistoryId.toString());
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }

  private String toWalletCursor(LocalDateTime createdAt, String walletEventId) {
    String payload =
        String.join(
            CURSOR_DELIMITER,
            CURSOR_VERSION,
            SeoulDateTimeUtils.toSeoulOffset(createdAt).toString(),
            walletEventId);
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }

  private String restoreBase64Padding(String cursor) {
    int remainder = cursor.length() % 4;
    if (remainder == 0) {
      return cursor;
    }
    if (remainder == 1) {
      throw new CustomException(PointErrorCode.INVALID_CURSOR);
    }
    return cursor + "=".repeat(4 - remainder);
  }

  private PointReferenceMetaResponse resolveReferenceMeta(
      PointReferenceType referenceType,
      Long referenceId,
      Map<Long, PointHistoryReferenceMetaProjection> crewMeta,
      Map<Long, PointHistoryReferenceMetaProjection> settlementMeta) {
    if (referenceId == null) {
      return null;
    }

    if (referenceType == PointReferenceType.CREW_PARTICIPANT) {
      PointHistoryReferenceMetaProjection projection = crewMeta.get(referenceId);
      return projection == null
          ? null
          : new PointReferenceMetaResponse(projection.crewId(), projection.crewTitle());
    }

    if (referenceType == PointReferenceType.SETTLEMENT_ITEM) {
      PointHistoryReferenceMetaProjection projection = settlementMeta.get(referenceId);
      return projection == null
          ? null
          : new PointReferenceMetaResponse(projection.crewId(), projection.crewTitle());
    }

    return null;
  }

  private Map<Long, PointHistoryReferenceMetaProjection> resolveReferenceMeta(
      UUID memberUuid, List<PointHistoryItemProjection> rows, PointReferenceType targetType) {
    Set<Long> referenceIds =
        rows.stream()
            .filter(row -> row.referenceType() == targetType && row.referenceId() != null)
            .map(PointHistoryItemProjection::referenceId)
            .collect(Collectors.toSet());

    if (referenceIds.isEmpty()) {
      return Collections.emptyMap();
    }

    if (targetType == PointReferenceType.CREW_PARTICIPANT) {
      return pointHistoryQueryRepository.findCrewParticipantReferenceMeta(memberUuid, referenceIds);
    }

    if (targetType == PointReferenceType.SETTLEMENT_ITEM) {
      return pointHistoryQueryRepository.findSettlementItemReferenceMeta(memberUuid, referenceIds);
    }

    return Collections.emptyMap();
  }

  private Map<Long, PointHistoryReferenceMetaProjection> resolveWalletReferenceMeta(
      UUID memberUuid, List<WalletHistoryEventProjection> rows, PointReferenceType targetType) {
    Set<Long> referenceIds =
        rows.stream()
            .filter(row -> row.referenceType() == targetType && row.referenceId() != null)
            .map(WalletHistoryEventProjection::referenceId)
            .collect(Collectors.toSet());

    if (referenceIds.isEmpty()) {
      return Collections.emptyMap();
    }

    if (targetType == PointReferenceType.CREW_PARTICIPANT) {
      return pointHistoryQueryRepository.findCrewParticipantReferenceMeta(memberUuid, referenceIds);
    }

    if (targetType == PointReferenceType.SETTLEMENT_ITEM) {
      return pointHistoryQueryRepository.findSettlementItemReferenceMeta(memberUuid, referenceIds);
    }

    return Collections.emptyMap();
  }

  private record Cursor(LocalDateTime createdAt, Long pointHistoryId) {}

  private record WalletCursor(LocalDateTime createdAt, String walletEventId) {}
}
