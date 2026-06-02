package com.oit.dondok.domain.mission.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.config.JpaAuditingConfig;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.entity.CertificationStatus;
import com.oit.dondok.domain.mission.entity.MissionLog;
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
    MissionLog log = newInstance(MissionLog.class);
    ReflectionTestUtils.setField(log, "crewParticipant", participant);
    ReflectionTestUtils.setField(log, "imageS3Key", "mission/log/" + imageHash);
    ReflectionTestUtils.setField(log, "caption", "오늘도 인증 완료");
    ReflectionTestUtils.setField(log, "imageHash", imageHash);
    ReflectionTestUtils.setField(log, "serverTime", LocalDateTime.of(2026, 6, 2, 8, 0));
    ReflectionTestUtils.setField(log, "certificationStatus", CertificationStatus.SUCCESS);
    return entityManager.persistAndFlush(log);
  }

  private static <T> T newInstance(Class<T> entityClass) throws Exception {
    Constructor<T> constructor = entityClass.getDeclaredConstructor();
    constructor.setAccessible(true);
    return constructor.newInstance();
  }
}
