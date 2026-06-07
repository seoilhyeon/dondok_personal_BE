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
  PARTICIPANT_NOT_ELIGIBLE(HttpStatus.CONFLICT, "인증을 제출할 수 있는 상태가 아닙니다."),
  MISSION_NOT_STARTED(HttpStatus.CONFLICT, "아직 미션 시작 전입니다."),
  MISSION_ENDED(HttpStatus.CONFLICT, "미션 기간이 종료되었습니다."),
  NOT_MISSION_DAY(HttpStatus.CONFLICT, "오늘은 미션 인증 가능일이 아닙니다."),
  ALREADY_CERTIFIED_TODAY(HttpStatus.CONFLICT, "오늘은 이미 인증을 완료했습니다."),
  CERTIFICATION_IN_REVIEW(HttpStatus.CONFLICT, "검토 중인 인증이 있습니다.");

  private final HttpStatus status;
  private final String message;

  @Override
  public String getCode() {
    return name();
  }
}
