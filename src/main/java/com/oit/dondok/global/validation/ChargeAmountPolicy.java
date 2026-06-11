package com.oit.dondok.global.validation;

public final class ChargeAmountPolicy {

  private static final long MIN_CHARGE_AMOUNT = 1_000L;
  private static final long MAX_CHARGE_AMOUNT = 1_000_000L;
  private static final long CHARGE_AMOUNT_STEP = 1_000L;

  private ChargeAmountPolicy() {}

  public static boolean isValid(Long amount) {
    return amount != null
        && amount >= MIN_CHARGE_AMOUNT
        && amount <= MAX_CHARGE_AMOUNT
        && amount % CHARGE_AMOUNT_STEP == 0;
  }
}
