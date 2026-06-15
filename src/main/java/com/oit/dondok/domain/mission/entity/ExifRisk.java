package com.oit.dondok.domain.mission.entity;

public enum ExifRisk {
  NORMAL(true), // 촬영 시각 [당일 00:00, server_time] 윈도우 안
  MISSING(true), // EXIF 촬영 시각 없음(또는 추출 실패)
  TIME_INVALID(false); // 윈도우 밖 (어제 사진이거나, server_time 이후)

  private final boolean autoApprovable;

  ExifRisk(boolean autoApprovable) {
    this.autoApprovable = autoApprovable;
  }

  public boolean isAutoApprovable() {
    return autoApprovable;
  }
}
