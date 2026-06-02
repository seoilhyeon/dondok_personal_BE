package com.oit.dondok.domain.mission.entity;

public enum ExifRisk {
  NORMAL, // 촬영 시각 [당일 00:00, server_time] 윈도우 안
  MISSING, // EXIF 촬영 시각 없음(또는 추출 실패)
  TIME_INVALID // 윈도우 밖 (어제 사진이거나, server_time 이후)
}
