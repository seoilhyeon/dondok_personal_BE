package com.oit.dondok.domain.mission.entity;

public enum MissionFailureReason {
  EXIF_MISSING, // MVP에서는 EXIF 없음 이미지를 자동 승인하므로 향후 정책 변경을 위한 예비 사유다.
  EXIF_TIME_INVALID,
  DUPLICATE_IMAGE_HASH,
  BEFORE_START,
  AFTER_END
}
