package com.oit.dondok.domain.mission.service;

import com.oit.dondok.domain.mission.entity.MissionLog;
import org.springframework.stereotype.Component;

@Component
public class AutoCertificationDecider {

  // 저장된 EXIF 위험도와 중복 해시 값을 기준으로 자동 승인/반려를 판정한다.
  public boolean isApproved(MissionLog missionLog) {
    if (missionLog.isDuplicateHash()) {
      return false;
    }

    return missionLog.getExifRisk().isAutoApprovable();
  }
}
