package com.oit.dondok.domain.notification.entity;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.global.entity.AuditableTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "notification_settings",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_notification_settings_member", columnNames = "member_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSettings extends AuditableTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Column(name = "cat_emoji_reaction", nullable = false)
  private Boolean catEmojiReaction;

  @Column(name = "cat_host_verification", nullable = false)
  private Boolean catHostVerification;

  @Column(name = "cat_deadline_approaching", nullable = false)
  private Boolean catDeadlineApproaching;

  @Column(name = "cat_daily_result", nullable = false)
  private Boolean catDailyResult;

  @Column(name = "cat_settlement", nullable = false)
  private Boolean catSettlement;

  @Column(name = "cat_crew_disbanded", nullable = false)
  private Boolean catCrewDisbanded;

  @Column(name = "cat_crew_news", nullable = false)
  private Boolean catCrewNews;

  @Column(name = "quiet_start_time")
  private LocalTime quietStartTime;

  @Column(name = "quiet_end_time")
  private LocalTime quietEndTime;

  public static NotificationSettings createDefault(Member member) {
    NotificationSettings s = new NotificationSettings();
    s.member = member;
    s.catEmojiReaction = true;
    s.catHostVerification = true;
    s.catDeadlineApproaching = true;
    s.catDailyResult = true;
    s.catSettlement = true;
    s.catCrewDisbanded = true;
    s.catCrewNews = true;
    return s;
  }

  public void update(
      Map<NotificationCategory, Boolean> categories, LocalTime quietStart, LocalTime quietEnd) {
    if (categories != null) {
      if (categories.containsKey(NotificationCategory.EMOJI_REACTION)) {
        this.catEmojiReaction = categories.get(NotificationCategory.EMOJI_REACTION);
      }
      if (categories.containsKey(NotificationCategory.HOST_VERIFICATION)) {
        this.catHostVerification = categories.get(NotificationCategory.HOST_VERIFICATION);
      }
      if (categories.containsKey(NotificationCategory.DEADLINE_APPROACHING)) {
        this.catDeadlineApproaching = categories.get(NotificationCategory.DEADLINE_APPROACHING);
      }
      if (categories.containsKey(NotificationCategory.DAILY_RESULT)) {
        this.catDailyResult = categories.get(NotificationCategory.DAILY_RESULT);
      }
      if (categories.containsKey(NotificationCategory.SETTLEMENT)) {
        this.catSettlement = categories.get(NotificationCategory.SETTLEMENT);
      }
      if (categories.containsKey(NotificationCategory.CREW_DISBANDED)) {
        this.catCrewDisbanded = categories.get(NotificationCategory.CREW_DISBANDED);
      }
      if (categories.containsKey(NotificationCategory.CREW_NEWS)) {
        this.catCrewNews = categories.get(NotificationCategory.CREW_NEWS);
      }
    }
    this.quietStartTime = quietStart;
    this.quietEndTime = quietEnd;
  }

  public Map<NotificationCategory, Boolean> categoryMap() {
    Map<NotificationCategory, Boolean> map = new EnumMap<>(NotificationCategory.class);
    map.put(NotificationCategory.EMOJI_REACTION, catEmojiReaction);
    map.put(NotificationCategory.HOST_VERIFICATION, catHostVerification);
    map.put(NotificationCategory.DEADLINE_APPROACHING, catDeadlineApproaching);
    map.put(NotificationCategory.DAILY_RESULT, catDailyResult);
    map.put(NotificationCategory.SETTLEMENT, catSettlement);
    map.put(NotificationCategory.CREW_DISBANDED, catCrewDisbanded);
    map.put(NotificationCategory.CREW_NEWS, catCrewNews);
    return map;
  }
}
