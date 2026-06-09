package com.oit.dondok.domain.mission.service;

import com.oit.dondok.domain.mission.entity.MissionFailureReason;

// 자동 인증 판정 결과와 실패 사유를 함께 전달한다.
public record AutoCertificationDecision(boolean approved, MissionFailureReason failureReason) {

  public static AutoCertificationDecision approve() {
    return new AutoCertificationDecision(true, null);
  }

  public static AutoCertificationDecision reject(MissionFailureReason failureReason) {
    return new AutoCertificationDecision(false, failureReason);
  }
}
