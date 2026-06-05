package com.oit.dondok.domain.point.entity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PointHistoryIdempotencyKeyValidator {

  private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 160;
  private static final Pattern CHARGE_IDEMPOTENCY_KEY_PATTERN =
      Pattern.compile("^charge:[A-Za-z0-9_-]+$");
  private static final Pattern CREW_RESERVE_IDEMPOTENCY_KEY_PATTERN =
      Pattern.compile(
          "^crew:([1-9][0-9]*):participant:([1-9][0-9]*):reserve(?:-lock)?:([1-9][0-9]*)$");
  private static final Pattern CREW_RESERVE_RELEASE_IDEMPOTENCY_KEY_PATTERN =
      Pattern.compile(
          "^crew:([1-9][0-9]*):participant:([1-9][0-9]*):reserve-release:([1-9][0-9]*)$");
  private static final Pattern CREW_SETTLEMENT_REFUND_IDEMPOTENCY_KEY_PATTERN =
      Pattern.compile("^crew:([1-9][0-9]*):participant:([1-9][0-9]*):settlement-refund:final$");

  private PointHistoryIdempotencyKeyValidator() {}

  static void validate(
      PointTransactionType transactionType,
      PointReferenceType referenceType,
      Long referenceId,
      String idempotencyKey) {
    validateReferenceMapping(transactionType, referenceType, referenceId);
    validateIdempotencyKey(transactionType, referenceId, idempotencyKey);
  }

  private static void validateReferenceMapping(
      PointTransactionType transactionType, PointReferenceType referenceType, Long referenceId) {
    if (transactionType == PointTransactionType.POINT_CHARGE) {
      if (referenceType != PointReferenceType.POINT_CHARGE || referenceId != 0) {
        throw new IllegalArgumentException("포인트 충전 참조 정보가 올바르지 않습니다.");
      }
      return;
    }
    if (transactionType == PointTransactionType.CREW_SETTLEMENT_REFUND) {
      if (referenceType != PointReferenceType.SETTLEMENT_ITEM || referenceId <= 0) {
        throw new IllegalArgumentException("정산 환급 참조 정보가 올바르지 않습니다.");
      }
      return;
    }
    if (referenceType != PointReferenceType.CREW_PARTICIPANT || referenceId <= 0) {
      throw new IllegalArgumentException("크루 포인트 참조 정보가 올바르지 않습니다.");
    }
  }

  private static void validateIdempotencyKey(
      PointTransactionType transactionType, Long referenceId, String idempotencyKey) {
    if (idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("idempotencyKey는 빈 값일 수 없습니다.");
    }
    if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
      throw new IllegalArgumentException("idempotencyKey는 160자를 초과할 수 없습니다.");
    }

    switch (transactionType) {
      case POINT_CHARGE ->
          validateKeyMatches(
              idempotencyKey, CHARGE_IDEMPOTENCY_KEY_PATTERN, "포인트 충전 멱등성 키가 올바르지 않습니다.");
      case CREW_DEPOSIT_RESERVE ->
          validateCrewParticipantKey(
              referenceId,
              idempotencyKey,
              CREW_RESERVE_IDEMPOTENCY_KEY_PATTERN,
              "보증금 예치 멱등성 키가 올바르지 않습니다.");
      case CREW_RESERVE_RELEASE ->
          validateCrewParticipantKey(
              referenceId,
              idempotencyKey,
              CREW_RESERVE_RELEASE_IDEMPOTENCY_KEY_PATTERN,
              "보증금 반환 멱등성 키가 올바르지 않습니다.");
      case CREW_SETTLEMENT_REFUND ->
          validateKeyMatches(
              idempotencyKey,
              CREW_SETTLEMENT_REFUND_IDEMPOTENCY_KEY_PATTERN,
              "정산 환급 멱등성 키가 올바르지 않습니다.");
    }
  }

  private static void validateCrewParticipantKey(
      Long referenceId, String idempotencyKey, Pattern pattern, String message) {
    Matcher matcher = pattern.matcher(idempotencyKey);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(message);
    }
    long participantId = parsePositiveId(matcher.group(2), message);
    if (participantId != referenceId) {
      throw new IllegalArgumentException("멱등성 키의 participantId와 referenceId가 일치하지 않습니다.");
    }
  }

  private static long parsePositiveId(String value, String message) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(message);
    }
  }

  private static void validateKeyMatches(String idempotencyKey, Pattern pattern, String message) {
    if (!pattern.matcher(idempotencyKey).matches()) {
      throw new IllegalArgumentException(message);
    }
  }
}
