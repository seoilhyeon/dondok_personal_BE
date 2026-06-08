package com.oit.dondok.domain.point.service;

import com.oit.dondok.domain.point.entity.PointTransactionType;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public enum PointHistoryTypeFilter {
  CHARGE("charge", PointTransactionType.POINT_CHARGE),
  REFUND("refund", PointTransactionType.CREW_RESERVE_RELEASE),
  DEPOSIT(
      "deposit", PointTransactionType.CREW_DEPOSIT_RESERVE, PointTransactionType.CREW_DEPOSIT_LOCK),
  WITHDRAWAL("withdrawal"),
  SETTLEMENT("settlement", PointTransactionType.CREW_SETTLEMENT_REFUND);

  private final String queryValue;
  private final Set<PointTransactionType> transactionTypes;

  PointHistoryTypeFilter(String queryValue, PointTransactionType... transactionTypes) {
    this.queryValue = queryValue;
    this.transactionTypes = Set.of(transactionTypes);
  }

  public static PointHistoryTypeFilter from(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    String normalized = value.trim().toLowerCase(Locale.ROOT);
    return Arrays.stream(values())
        .filter(filter -> filter.queryValue.equals(normalized))
        .findFirst()
        .orElseThrow(IllegalArgumentException::new);
  }

  public static String supportedQueryValues() {
    return Arrays.stream(values())
        .map(filter -> filter.queryValue)
        .collect(Collectors.joining(", "));
  }

  public Set<PointTransactionType> transactionTypes() {
    return Collections.unmodifiableSet(transactionTypes);
  }
}
