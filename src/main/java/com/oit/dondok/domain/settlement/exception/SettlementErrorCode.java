package com.oit.dondok.domain.settlement.exception;

import com.oit.dondok.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SettlementErrorCode implements ErrorCode {
  SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "정산 정보를 찾을 수 없습니다.");

  private final HttpStatus status;
  private final String message;

  @Override
  public String getCode() {
    return this.name();
  }
}
