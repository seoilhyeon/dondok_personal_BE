package com.oit.dondok.domain.crew.exception;

import com.oit.dondok.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CrewErrorCode implements ErrorCode {
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력 값이 유효하지 않습니다."),
  INVALID_CATEGORY(HttpStatus.BAD_REQUEST, "유효하지 않은 카테고리입니다."),
  INVALID_DEPOSIT_AMOUNT(HttpStatus.BAD_REQUEST, "보증금은 1,000원 단위로 1,000원 이상 100,000원 이하여야 합니다."),
  INVALID_FREQUENCY_RULE(HttpStatus.BAD_REQUEST, "SPECIFIC_DAYS 타입에는 미션 수행 요일을 하나 이상 지정해야 합니다."),
  HOST_AGREEMENT_REQUIRED(HttpStatus.BAD_REQUEST, "호스트 약관 동의는 필수입니다."),
  CONCURRENT_PAYMENT_ERROR(HttpStatus.CONFLICT, "동시 결제 오류가 발생했습니다. 다시 시도해 주세요."),
  INVALID_CURSOR(HttpStatus.BAD_REQUEST, "유효하지 않은 커서 값입니다."),
  MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
  CREW_NOT_FOUND(HttpStatus.NOT_FOUND, "크루를 찾을 수 없습니다."),
  PARTICIPANT_NOT_FOUND(HttpStatus.NOT_FOUND, "참여자를 찾을 수 없습니다.");

  private final HttpStatus status;
  private final String message;

  @Override
  public String getCode() {
    return name();
  }
}
