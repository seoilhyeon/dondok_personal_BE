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
  CREW_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 크루에 접근 권한이 없습니다."),
  CREW_NOT_RECRUITING(HttpStatus.BAD_REQUEST, "모집 중인 크루가 아닙니다."),
  CAPACITY_FULL(HttpStatus.BAD_REQUEST, "크루 정원이 가득 찼습니다."),
  ALREADY_PARTICIPATING(HttpStatus.CONFLICT, "이미 참여 중인 크루입니다."),
  APPLICATION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "신청이 허용되지 않는 상태입니다."),
  PARTICIPANT_NOT_FOUND(HttpStatus.NOT_FOUND, "참여 이력을 찾을 수 없습니다."),
  PARTICIPANT_NOT_IN_CREW(HttpStatus.NOT_FOUND, "해당 크루에 속한 참여자가 아닙니다."),
  APPLICATION_NOT_CANCELLABLE(HttpStatus.BAD_REQUEST, "취소할 수 없는 신청 상태입니다."),

  // === AI 크루 생성 도우미 예외 규격 추가 ===
  AI_RECOMMENDATION_FAILED(HttpStatus.BAD_GATEWAY, "AI 추천 호출에 실패했습니다."),
  AI_RESPONSE_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "AI의 응답 형식이 유효하지 않습니다."),

  // === HOST 운영 콘솔 ===
  FORBIDDEN_NOT_HOST(HttpStatus.FORBIDDEN, "크루 방장만 접근할 수 있습니다."),
  APPLICATION_NOT_APPROVABLE(HttpStatus.BAD_REQUEST, "승인할 수 없는 신청 상태입니다."),
  APPLICATION_NOT_REJECTABLE(HttpStatus.BAD_REQUEST, "거절할 수 없는 신청 상태입니다."),
  CREW_DISBAND_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "모집 중이거나 활동 중인 크루만 해체할 수 있습니다."),

  // === 크루 공지 ===
  NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "공지를 찾을 수 없습니다."),
  INVALID_REACTION_TYPE(HttpStatus.BAD_REQUEST, "리액션 값이 비어 있거나 너무 깁니다."),
  REACTION_NOT_ALLOWED(HttpStatus.FORBIDDEN, "리액션할 수 없는 공지입니다.");

  private final HttpStatus status;
  private final String message;

  @Override
  public String getCode() {
    return name();
  }
}
