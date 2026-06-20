package com.oit.dondok.domain.notification.entity;

import java.util.Map;
import java.util.Set;

public enum NotificationCategory {
  EMOJI_REACTION,
  HOST_VERIFICATION,
  DEADLINE_APPROACHING,
  DAILY_RESULT,
  SETTLEMENT,
  CREW_DISBANDED,
  CREW_NEWS;

  private static final Map<String, Set<NotificationCategory>> EVENT_TYPE_MAP =
      Map.ofEntries(
          Map.entry("CREW_APPLICATION_RECEIVED", Set.of(HOST_VERIFICATION)),
          Map.entry("CREW_APPLICATION_WITHDRAWN", Set.of(HOST_VERIFICATION)),
          Map.entry("MISSION_LOG_UPLOADED", Set.of(HOST_VERIFICATION)),
          Map.entry("UNREVIEWED_MISSION_LOG_EXISTS", Set.of(HOST_VERIFICATION)),
          Map.entry("MISSION_DEADLINE_APPROACHING", Set.of(DEADLINE_APPROACHING)),
          Map.entry("MISSION_LOG_VERIFICATION_RESULT", Set.of(DAILY_RESULT)),
          Map.entry("DAILY_SETTLEMENT_COMPLETED", Set.of(DAILY_RESULT)),
          Map.entry("SETTLEMENT_COMPLETED", Set.of(SETTLEMENT)),
          Map.entry("CREW_DISBANDED", Set.of(CREW_DISBANDED)),
          Map.entry("CREW_NOTICE_POSTED", Set.of(CREW_NEWS)),
          Map.entry("CREW_NOTICE_COMMENT_ADDED", Set.of(CREW_NEWS)),
          Map.entry("CREW_NOTICE_REACTION_ADDED", Set.of(CREW_NEWS, EMOJI_REACTION)),
          Map.entry("CREW_CLOSE_SOON", Set.of(CREW_NEWS)),
          Map.entry("CREW_ACTIVATED", Set.of(CREW_NEWS)),
          Map.entry("FEED_REACTION_ADDED", Set.of(EMOJI_REACTION)));

  /** 이벤트 타입에 대응하는 카테고리 집합. 매핑 없으면 빈 집합 반환(설정 무관 발송). */
  public static Set<NotificationCategory> forEventType(String eventType) {
    return EVENT_TYPE_MAP.getOrDefault(eventType, Set.of());
  }
}
