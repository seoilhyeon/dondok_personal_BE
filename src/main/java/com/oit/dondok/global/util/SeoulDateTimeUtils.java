package com.oit.dondok.global.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SeoulDateTimeUtils {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

  public static OffsetDateTime toSeoulOffset(LocalDateTime ldt) {
    return ldt == null ? null : ldt.atZone(SEOUL_ZONE).toOffsetDateTime();
  }
}
