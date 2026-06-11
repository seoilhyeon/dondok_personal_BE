package com.oit.dondok.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ChargeAmountValidator implements ConstraintValidator<ValidChargeAmount, Long> {

  @Override
  public boolean isValid(Long value, ConstraintValidatorContext context) {
    return ChargeAmountPolicy.isValid(value);
  }
}
