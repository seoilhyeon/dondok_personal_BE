package com.oit.dondok.domain.point.service;

import com.oit.dondok.domain.point.entity.WalletHistoryDisplayType;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

public enum WalletHistoryTypeFilter {
  CHARGE("charge", WalletHistoryDisplayType.DODIN_CHARGE),
  REFUND("refund", WalletHistoryDisplayType.DODIN_DEPOSIT_REFUND),
  DEPOSIT("deposit", WalletHistoryDisplayType.DODIN_DEPOSIT),
  WITHDRAWAL("withdrawal", WalletHistoryDisplayType.DODIN_WITHDRAWAL),
  SETTLEMENT("settlement", WalletHistoryDisplayType.SETTLEMENT_REFUND);

  private final String queryValue;
  private final Set<WalletHistoryDisplayType> displayTypes;

  WalletHistoryTypeFilter(String queryValue, WalletHistoryDisplayType... displayTypes) {
    this.queryValue = queryValue;
    this.displayTypes = Set.of(displayTypes);
  }

  public static WalletHistoryTypeFilter from(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    String normalized = value.trim().toLowerCase(Locale.ROOT);
    return Arrays.stream(values())
        .filter(filter -> filter.queryValue.equals(normalized))
        .findFirst()
        .orElseThrow(IllegalArgumentException::new);
  }

  public Set<WalletHistoryDisplayType> displayTypes() {
    return Collections.unmodifiableSet(displayTypes);
  }
}
