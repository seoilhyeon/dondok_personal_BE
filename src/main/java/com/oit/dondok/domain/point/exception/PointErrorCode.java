package com.oit.dondok.domain.point.exception;

import com.oit.dondok.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PointErrorCode implements ErrorCode {
  POINT_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "포인트 계좌를 찾을 수 없습니다."),
  INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "포인트 금액이 유효하지 않습니다."),
  INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "포인트 잔액이 부족합니다."),
  IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "동일한 멱등성 키로 다른 포인트 요청이 감지되었습니다."),
  INVALID_POINT_REFERENCE(HttpStatus.BAD_REQUEST, "포인트 참조 정보가 유효하지 않습니다."),
  INVALID_LIMIT(HttpStatus.BAD_REQUEST, "조회 개수가 유효하지 않습니다."),
  INVALID_CURSOR(HttpStatus.BAD_REQUEST, "커서가 유효하지 않습니다."),
  INVALID_HISTORY_TYPE(HttpStatus.BAD_REQUEST, "포인트 내역 유형 필터가 유효하지 않습니다."),
  INVALID_HISTORY_MONTH(HttpStatus.BAD_REQUEST, "포인트 내역 월 필터가 유효하지 않습니다.");

  private final HttpStatus status;
  private final String message;

  @Override
  public String getCode() {
    return name();
  }
}
