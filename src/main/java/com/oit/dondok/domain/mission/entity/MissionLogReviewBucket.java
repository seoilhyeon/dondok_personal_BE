package com.oit.dondok.domain.mission.entity;

import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import java.util.Locale;

public enum MissionLogReviewBucket {
  URGENT("urgent"),
  WARNING("warning"),
  NORMAL("normal"),
  DECIDED("decided");

  private final String value;

  MissionLogReviewBucket(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  // 요청 파라미터의 소문자 bucket 값을 서버 enum으로 변환한다.
  public static MissionLogReviewBucket from(String value) {
    if (value == null || value.isBlank()) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
    try {
      return MissionLogReviewBucket.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
  }
}
