package com.oit.dondok.domain.mission.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.config.JpaAuditingConfig;
import com.oit.dondok.config.QuerydslConfig;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.ExifRisk;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.mission.entity.MissionLog;
import com.oit.dondok.domain.mission.entity.MissionLogReviewBucket;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.entity.MissionScheduleDay;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
@Import({JpaAuditingConfig.class, QuerydslConfig.class, MissionLogQueryRepository.class})
class MissionLogQueryRepositoryDeadlineReminderTest {

  // 2026-06-21 일요일 — getDayOfWeek().getValue() == 7
  private static final LocalDateTime TODAY_START = LocalDateTime.of(2026, 6, 21, 0, 0);
  private static final LocalDateTime TODAY_END = LocalDateTime.of(2026, 6, 22, 0, 0);
  private static final int SUNDAY = 7;
  private static final int MONDAY = 1;

  @Autowired private TestEntityManager em;
  @Autowired private MissionLogQueryRepository missionLogQueryRepository;

  // SPECIFIC_DAYS 크루에서 오늘 요일이 스케줄에 포함되면 알림 대상이다.
  @Test
  void specificDaysCrewTodayInScheduleIncluded() {
    Member host = persistMember("host-a@test.com", "호스트A");
    Crew crew = persistActiveCrew(host, "스케줄포함크루");
    MissionRule rule =
        persistMissionRule(crew, MissionFrequencyType.SPECIFIC_DAYS, DailySettlementType.A);
    persistScheduleDay(rule, SUNDAY); // 오늘(일요일) 포함
    CrewParticipant participant = persistLockedParticipant(crew, host);

    List<CrewParticipant> result =
        missionLogQueryRepository.findDeadlineReminderTargets(
            DailySettlementType.A, TODAY_START, TODAY_END, SUNDAY, 0L, 10);

    assertThat(result).extracting(CrewParticipant::getId).contains(participant.getId());
  }

  // SPECIFIC_DAYS 크루에서 오늘 요일이 스케줄에 없으면 알림 대상에서 제외된다.
  @Test
  void specificDaysCrewTodayNotInScheduleExcluded() {
    Member host = persistMember("host-b@test.com", "호스트B");
    Crew crew = persistActiveCrew(host, "스케줄미포함크루");
    MissionRule rule =
        persistMissionRule(crew, MissionFrequencyType.SPECIFIC_DAYS, DailySettlementType.A);
    persistScheduleDay(rule, MONDAY); // 월요일만 스케줄, 오늘(일요일)은 없음
    persistLockedParticipant(crew, host);

    List<CrewParticipant> result =
        missionLogQueryRepository.findDeadlineReminderTargets(
            DailySettlementType.A, TODAY_START, TODAY_END, SUNDAY, 0L, 10);

    assertThat(result).isEmpty();
  }

  // DAILY 크루는 요일에 관계없이 항상 알림 대상이다.
  @Test
  void dailyCrewAnyDayOfWeekIncluded() {
    Member host = persistMember("host-c@test.com", "호스트C");
    Crew crew = persistActiveCrew(host, "데일리크루");
    persistMissionRule(crew, MissionFrequencyType.DAILY, DailySettlementType.A);
    CrewParticipant participant = persistLockedParticipant(crew, host);

    List<CrewParticipant> result =
        missionLogQueryRepository.findDeadlineReminderTargets(
            DailySettlementType.A, TODAY_START, TODAY_END, SUNDAY, 0L, 10);

    assertThat(result).extracting(CrewParticipant::getId).contains(participant.getId());
  }

  // DECIDED 버킷은 배치 실행 시각 기준 자동 판정 전인 수동 결정 로그만 조회한다.
  @Test
  void decidedBucketConstrainedByBatchExecutionTime() {
    LocalDateTime batchExecutionTime = LocalDateTime.of(2026, 6, 22, 0, 0);
    Member host = persistMember("host-decided@test.com", "호스트D");
    Crew crew = persistActiveCrew(host, "수동결정크루");
    persistMissionRule(crew, MissionFrequencyType.DAILY, DailySettlementType.B);
    CrewParticipant participant = persistLockedParticipant(crew, host);
    MissionLog included =
        persistManualApproveLog(
            participant,
            host,
            "HASH_DECIDED_INCLUDED",
            LocalDateTime.of(2026, 6, 21, 8, 0),
            LocalDateTime.of(2026, 6, 21, 10, 0));
    persistManualApproveLog(
        participant,
        host,
        "HASH_DECIDED_EXCLUDED",
        LocalDateTime.of(2026, 6, 20, 8, 0),
        LocalDateTime.of(2026, 6, 20, 10, 0));
    em.flush();
    em.clear();

    List<MissionLog> page =
        missionLogQueryRepository.findReviewablePageByCrewId(
            crew.getId(), MissionLogReviewBucket.DECIDED, null, null, 10, batchExecutionTime);
    long count =
        missionLogQueryRepository.countReviewableByCrewIdAndBucket(
            crew.getId(), MissionLogReviewBucket.DECIDED, batchExecutionTime);

    assertThat(page).extracting(MissionLog::getId).containsExactly(included.getId());
    assertThat(count).isEqualTo(1L);
  }

  private Member persistMember(String email, String nickname) {
    return em.persistAndFlush(Member.create(email, "pw-hash", nickname));
  }

  private Crew persistActiveCrew(Member host, String title) {
    LocalDateTime recruitAt = LocalDateTime.of(2026, 6, 1, 9, 0);
    Crew crew =
        Crew.create(
            host,
            title,
            title + " 설명",
            null,
            "OTHER",
            "{}",
            HostPolicyVersion.HOST_POLICY_V1,
            recruitAt,
            10_000L,
            2,
            5,
            recruitAt.plusDays(3),
            recruitAt.plusDays(4),
            recruitAt.plusDays(30));
    crew.activate(recruitAt.plusDays(4));
    return em.persistAndFlush(crew);
  }

  private MissionRule persistMissionRule(
      Crew crew, MissionFrequencyType frequencyType, DailySettlementType settlementType) {
    return em.persistAndFlush(MissionRule.create(crew, frequencyType, settlementType));
  }

  private MissionScheduleDay persistScheduleDay(MissionRule rule, int dayOfWeek) {
    return em.persistAndFlush(MissionScheduleDay.create(rule, dayOfWeek));
  }

  private CrewParticipant persistLockedParticipant(Crew crew, Member member) {
    return em.persistAndFlush(
        CrewParticipant.create(crew, member, 10_000L, LocalDateTime.of(2026, 6, 5, 9, 0)));
  }

  private MissionLog persistManualApproveLog(
      CrewParticipant participant,
      Member moderator,
      String imageHash,
      LocalDateTime serverTime,
      LocalDateTime decidedAt) {
    MissionLog missionLog =
        MissionLog.createPendingReview(
            participant,
            "mission/log/" + imageHash,
            "오늘도 인증 완료",
            imageHash,
            serverTime,
            ExifRisk.NORMAL,
            false,
            serverTime);
    missionLog.approveManually(moderator, decidedAt);
    return em.persistAndFlush(missionLog);
  }
}
