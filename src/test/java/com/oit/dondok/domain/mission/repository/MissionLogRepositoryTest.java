package com.oit.dondok.domain.mission.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.config.JpaAuditingConfig;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.entity.CertificationStatus;
import com.oit.dondok.domain.mission.entity.ExifRisk;
import com.oit.dondok.domain.mission.entity.MissionLog;
import com.oit.dondok.domain.mission.entity.ModerationDecisionType;
import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@ActiveProfiles("test")
@DataJpaTest
@Import(JpaAuditingConfig.class)
class MissionLogRepositoryTest {

  @Autowired private TestEntityManager entityManager;
  @Autowired private MissionLogRepository missionLogRepository;

  // 같은 크루에 동일 image_hash 로그가 있으면 true.
  @Test
  void existsReturnsTrueWhenSameCrewHasImageHash() throws Exception {
    Member host = persistMember("a@example.com", "호스트A");
    Crew crew = persistCrew(host, "크루A");
    persistMissionLog(persistParticipant(crew, host), "HASH_A");

    assertThat(
            missionLogRepository.existsByCrewParticipantCrewIdAndImageHash(crew.getId(), "HASH_A"))
        .isTrue();
  }

  // 같은 크루라도 해시가 다르면 false.
  @Test
  void existsReturnsFalseForDifferentHashInSameCrew() throws Exception {
    Member host = persistMember("b@example.com", "호스트B");
    Crew crew = persistCrew(host, "크루B");
    persistMissionLog(persistParticipant(crew, host), "HASH_A");

    assertThat(
            missionLogRepository.existsByCrewParticipantCrewIdAndImageHash(
                crew.getId(), "HASH_OTHER"))
        .isFalse();
  }

  // 동일 해시가 다른 크루에만 있으면 현재 크루 기준으로는 false (크루 스코프 검증).
  @Test
  void existsReturnsFalseWhenHashBelongsToAnotherCrew() throws Exception {
    Member host = persistMember("c@example.com", "호스트C");
    Crew crewWithHash = persistCrew(host, "해시있는크루");
    persistMissionLog(persistParticipant(crewWithHash, host), "HASH_A");
    Crew otherCrew = persistCrew(host, "다른크루");

    assertThat(
            missionLogRepository.existsByCrewParticipantCrewIdAndImageHash(
                otherCrew.getId(), "HASH_A"))
        .isFalse();
  }

  // 당일 구간 안에 해당 상태 로그가 있으면 true.
  @Test
  void existsByDayWindowReturnsTrueWhenStatusLogWithinWindow() throws Exception {
    Member host = persistMember("d@example.com", "호스트D");
    Crew crew = persistCrew(host, "크루D");
    CrewParticipant participant = persistParticipant(crew, host);
    persistMissionLog(
        participant, "HASH_D", LocalDateTime.of(2026, 6, 2, 8, 0), CertificationStatus.SUCCESS);

    assertThat(
            missionLogRepository
                .existsByCrewParticipantIdAndCertificationStatusAndServerTimeGreaterThanEqualAndServerTimeLessThan(
                    participant.getId(),
                    CertificationStatus.SUCCESS,
                    LocalDateTime.of(2026, 6, 2, 0, 0),
                    LocalDateTime.of(2026, 6, 3, 0, 0)))
        .isTrue();
  }

  // 시작 경계(당일 00:00)는 포함된다 (>= start).
  @Test
  void existsByDayWindowIncludesDayStartBoundary() throws Exception {
    Member host = persistMember("e@example.com", "호스트E");
    Crew crew = persistCrew(host, "크루E");
    CrewParticipant participant = persistParticipant(crew, host);
    persistMissionLog(
        participant, "HASH_E", LocalDateTime.of(2026, 6, 2, 0, 0), CertificationStatus.SUCCESS);

    assertThat(
            missionLogRepository
                .existsByCrewParticipantIdAndCertificationStatusAndServerTimeGreaterThanEqualAndServerTimeLessThan(
                    participant.getId(),
                    CertificationStatus.SUCCESS,
                    LocalDateTime.of(2026, 6, 2, 0, 0),
                    LocalDateTime.of(2026, 6, 3, 0, 0)))
        .isTrue();
  }

  // 종료 경계(다음날 00:00)는 제외된다 (< end). 다음 날 슬롯 로그가 오늘로 새지 않는다.
  @Test
  void existsByDayWindowExcludesNextDayStartBoundary() throws Exception {
    Member host = persistMember("f@example.com", "호스트F");
    Crew crew = persistCrew(host, "크루F");
    CrewParticipant participant = persistParticipant(crew, host);
    persistMissionLog(
        participant, "HASH_F", LocalDateTime.of(2026, 6, 3, 0, 0), CertificationStatus.SUCCESS);

    assertThat(
            missionLogRepository
                .existsByCrewParticipantIdAndCertificationStatusAndServerTimeGreaterThanEqualAndServerTimeLessThan(
                    participant.getId(),
                    CertificationStatus.SUCCESS,
                    LocalDateTime.of(2026, 6, 2, 0, 0),
                    LocalDateTime.of(2026, 6, 3, 0, 0)))
        .isFalse();
  }

  // 상태가 다르면(FAILED) SUCCESS 조회에 걸리지 않는다 (FAILED만 있으면 재업로드 허용).
  @Test
  void existsByDayWindowFiltersByStatus() throws Exception {
    Member host = persistMember("g@example.com", "호스트G");
    Crew crew = persistCrew(host, "크루G");
    CrewParticipant participant = persistParticipant(crew, host);
    persistMissionLog(
        participant, "HASH_G", LocalDateTime.of(2026, 6, 2, 8, 0), CertificationStatus.FAILED);

    assertThat(
            missionLogRepository
                .existsByCrewParticipantIdAndCertificationStatusAndServerTimeGreaterThanEqualAndServerTimeLessThan(
                    participant.getId(),
                    CertificationStatus.SUCCESS,
                    LocalDateTime.of(2026, 6, 2, 0, 0),
                    LocalDateTime.of(2026, 6, 3, 0, 0)))
        .isFalse();
  }

  // 다른 참여자의 로그는 걸리지 않는다 (participant 스코프).
  @Test
  void existsByDayWindowScopedToParticipant() throws Exception {
    Member host = persistMember("h@example.com", "호스트H");
    Member other = persistMember("h2@example.com", "참여자H2");
    Crew crew = persistCrew(host, "크루H");
    CrewParticipant hostParticipant = persistParticipant(crew, host);
    CrewParticipant otherParticipant = persistParticipant(crew, other);
    persistMissionLog(
        hostParticipant, "HASH_H", LocalDateTime.of(2026, 6, 2, 8, 0), CertificationStatus.SUCCESS);

    assertThat(
            missionLogRepository
                .existsByCrewParticipantIdAndCertificationStatusAndServerTimeGreaterThanEqualAndServerTimeLessThan(
                    otherParticipant.getId(),
                    CertificationStatus.SUCCESS,
                    LocalDateTime.of(2026, 6, 2, 0, 0),
                    LocalDateTime.of(2026, 6, 3, 0, 0)))
        .isFalse();
  }

  @Test
  void findApprovedLogCandidatesReturnsManualAndAutoApproveSuccessLogsInWindow() throws Exception {
    Member host = persistMember("settlement-host@example.com", "host");
    Crew crew = persistCrew(host, "settlement crew");
    CrewParticipant participant = persistParticipant(crew, host);
    MissionLog manualApprove =
        persistMissionLog(
            participant,
            "HASH_MANUAL",
            LocalDateTime.of(2026, 6, 2, 8, 0),
            CertificationStatus.SUCCESS,
            ModerationDecisionType.MANUAL_APPROVE);
    MissionLog autoApprove =
        persistMissionLog(
            participant,
            "HASH_AUTO",
            LocalDateTime.of(2026, 6, 3, 8, 0),
            CertificationStatus.SUCCESS,
            ModerationDecisionType.AUTO_APPROVE);
    persistMissionLog(
        participant,
        "HASH_PENDING",
        LocalDateTime.of(2026, 6, 3, 9, 0),
        CertificationStatus.PENDING_REVIEW,
        null);
    persistMissionLog(
        participant,
        "HASH_REJECT",
        LocalDateTime.of(2026, 6, 3, 10, 0),
        CertificationStatus.FAILED,
        ModerationDecisionType.AUTO_REJECT);
    persistMissionLog(
        participant,
        "HASH_OUTSIDE",
        LocalDateTime.of(2026, 6, 4, 0, 0),
        CertificationStatus.SUCCESS,
        ModerationDecisionType.MANUAL_APPROVE);

    assertThat(
            missionLogRepository.findApprovedLogCandidatesForDailySettlementProjection(
                crew.getId(),
                LocalDateTime.of(2026, 6, 2, 0, 0),
                LocalDateTime.of(2026, 6, 4, 0, 0)))
        .extracting(MissionLog::getId)
        .containsExactlyInAnyOrder(manualApprove.getId(), autoApprove.getId());
  }

  private Member persistMember(String email, String nickname) {
    return entityManager.persistAndFlush(Member.create(email, "password-hash", nickname));
  }

  private Crew persistCrew(Member host, String title) {
    LocalDateTime now = LocalDateTime.of(2026, 5, 31, 9, 0);
    Crew crew =
        Crew.create(
            host,
            title,
            title + " 설명",
            null,
            "OTHER",
            "{}",
            HostPolicyVersion.HOST_POLICY_V1,
            now,
            10_000L,
            2,
            5,
            now.plusDays(3),
            now.plusDays(4),
            now.plusDays(30));
    return entityManager.persistAndFlush(crew);
  }

  private CrewParticipant persistParticipant(Crew crew, Member member) {
    return entityManager.persistAndFlush(
        CrewParticipant.create(crew, member, 10_000L, LocalDateTime.of(2026, 5, 31, 9, 0)));
  }

  private MissionLog persistMissionLog(CrewParticipant participant, String imageHash)
      throws Exception {
    return persistMissionLog(
        participant, imageHash, LocalDateTime.of(2026, 6, 2, 8, 0), CertificationStatus.SUCCESS);
  }

  private MissionLog persistMissionLog(
      CrewParticipant participant,
      String imageHash,
      LocalDateTime serverTime,
      CertificationStatus status)
      throws Exception {
    return persistMissionLog(participant, imageHash, serverTime, status, null);
  }

  private MissionLog persistMissionLog(
      CrewParticipant participant,
      String imageHash,
      LocalDateTime serverTime,
      CertificationStatus status,
      ModerationDecisionType decisionType)
      throws Exception {
    MissionLog log = newInstance(MissionLog.class);
    ReflectionTestUtils.setField(log, "crewParticipant", participant);
    ReflectionTestUtils.setField(log, "imageS3Key", "mission/log/" + imageHash);
    ReflectionTestUtils.setField(log, "caption", "오늘도 인증 완료");
    ReflectionTestUtils.setField(log, "imageHash", imageHash);
    ReflectionTestUtils.setField(log, "serverTime", serverTime);
    ReflectionTestUtils.setField(log, "exifRisk", ExifRisk.NORMAL);
    ReflectionTestUtils.setField(log, "certificationStatus", status);
    ReflectionTestUtils.setField(log, "decisionType", decisionType);
    if (status == CertificationStatus.FAILED) {
      ReflectionTestUtils.setField(log, "duplicateHash", true);
    }
    return entityManager.persistAndFlush(log);
  }

  private static <T> T newInstance(Class<T> entityClass) throws Exception {
    Constructor<T> constructor = entityClass.getDeclaredConstructor();
    constructor.setAccessible(true);
    return constructor.newInstance();
  }
}
