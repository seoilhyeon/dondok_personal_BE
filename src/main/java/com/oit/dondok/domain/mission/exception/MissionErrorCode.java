package com.oit.dondok.domain.mission.exception;

import com.oit.dondok.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MissionErrorCode implements ErrorCode {
  INVALID_IMAGE_KEY(HttpStatus.BAD_REQUEST, "유효하지 않은 이미지 key입니다."),
  MISSION_RULE_NOT_FOUND(HttpStatus.NOT_FOUND, "미션 규칙을 찾을 수 없습니다."),
  PARTICIPANT_NOT_ELIGIBLE(HttpStatus.FORBIDDEN, "인증을 제출할 수 있는 상태가 아닙니다."),
  MISSION_NOT_STARTED(HttpStatus.UNPROCESSABLE_ENTITY, "아직 미션 시작 전입니다."),
  MISSION_ENDED(HttpStatus.UNPROCESSABLE_ENTITY, "미션 기간이 종료되었습니다."),
  NOT_MISSION_DAY(HttpStatus.UNPROCESSABLE_ENTITY, "오늘은 미션 인증 가능일이 아닙니다."),
  ALREADY_CERTIFIED_TODAY(HttpStatus.CONFLICT, "오늘은 이미 인증을 완료했습니다."),
  CERTIFICATION_IN_REVIEW(HttpStatus.CONFLICT, "검토 중인 인증이 있습니다."),
  INVALID_CURSOR(HttpStatus.BAD_REQUEST, "유효하지 않은 커서 값입니다."),
  MISSION_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "미션 인증 로그를 찾을 수 없습니다."),
  REACTION_NOT_ALLOWED(HttpStatus.FORBIDDEN, "리액션할 수 없는 인증 로그입니다."),
  INVALID_REACTION_TYPE(HttpStatus.BAD_REQUEST, "리액션 값이 비어 있거나 너무 깁니다."),
  FORBIDDEN_NOT_HOST(HttpStatus.FORBIDDEN, "해당 크루의 방장만 수행할 수 있습니다."),
  MISSION_LOG_NOT_REVIEWABLE(HttpStatus.CONFLICT, "검수 대기 중인 인증만 처리할 수 있습니다."),
  SETTLEMENT_INPUT_FROZEN(HttpStatus.CONFLICT, "정산이 시작된 크루의 인증은 더 이상 검수할 수 없습니다."),
  REJECT_MEMO_REQUIRED(HttpStatus.BAD_REQUEST, "OTHER 거절 사유에는 메모가 필요합니다."),
  REJECT_MEMO_TOO_LONG(HttpStatus.BAD_REQUEST, "거절 사유 메모는 최대 50자입니다."),
  SYSTEM_MEMBER_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "System member not found."),
  INVALID_AUTO_APPROVAL_SIGNAL(
      HttpStatus.INTERNAL_SERVER_ERROR, "Auto approval requires valid image signals."),
  INVALID_AUTO_REJECTION_SIGNAL(
      HttpStatus.INTERNAL_SERVER_ERROR, "Auto rejection requires an image risk signal."),
  UNEXPECTED_MODERATION_DECISION_TYPE(
      HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected moderation decision type."),
  MISSION_MODERATION_SNAPSHOT_SERIALIZATION_FAILED(
      HttpStatus.INTERNAL_SERVER_ERROR, "미션 검토 이력 스냅샷 생성에 실패했습니다.");

  private final HttpStatus status;
  private final String message;

  @Override
  public String getCode() {
    return name();
  }
}
