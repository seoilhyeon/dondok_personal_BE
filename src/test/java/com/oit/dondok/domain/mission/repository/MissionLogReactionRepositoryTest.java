package com.oit.dondok.domain.mission.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.oit.dondok.config.JpaAuditingConfig;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.entity.CertificationStatus;
import com.oit.dondok.domain.mission.entity.ExifRisk;
import com.oit.dondok.domain.mission.entity.MissionLog;
import com.oit.dondok.domain.mission.entity.MissionLogReaction;
import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// 네이티브 upsert / 벌크 delete의 DB 레벨 멱등성 검증.
// ON DUPLICATE KEY UPDATE는 MySQL 전용 구문이라 H2로는 검증 불가 → 실제 MySQL(Testcontainers)로 부팅한다.
// integration 프로파일(create-drop, flyway off)이라 created_at/updated_at DB default가 없으므로,
// upsert가 timestamp를 명시 INSERT하는지가 여기서 함께 검증된다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("integration")
@Import(JpaAuditingConfig.class)
@Testcontainers
class MissionLogReactionRepositoryTest {

  private static final String CLAP = "👏";
  private static final String FIRE = "🔥";

  @Container @ServiceConnection
  static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

  @Autowired private TestEntityManager entityManager;
  @Autowired private MissionLogReactionRepository missionLogReactionRepository;

  private Long missionLogId;
  private Long memberId;

  @BeforeEach
  void setUp() throws Exception {
    Member member = persistMember("reactor@example.com", "리액터");
    Crew crew = persistCrew(member, "크루");
    CrewParticipant participant = persistParticipant(crew, member);
    MissionLog missionLog = persistMissionLog(participant);
    this.missionLogId = missionLog.getId();
    this.memberId = member.getId();
  }

  // upsert는 (mission_log_id, member_id, reaction_type) row를 생성한다.
  @Test
  void upsertInsertsReaction() {
    missionLogReactionRepository.upsert(missionLogId, memberId, CLAP);

    List<MissionLogReaction> reactions = missionLogReactionRepository.findAll();
    assertThat(reactions).hasSize(1);
    assertThat(reactions.get(0).getReactionType()).isEqualTo(CLAP);
  }

  // 같은 키로 두 번 upsert해도 unique 충돌이 에러 없이 흡수되어 row는 1개로 수렴한다(DB 레벨 멱등).
  @Test
  void upsertIsIdempotentForSameKey() {
    missionLogReactionRepository.upsert(missionLogId, memberId, CLAP);

    assertThatCode(() -> missionLogReactionRepository.upsert(missionLogId, memberId, CLAP))
        .doesNotThrowAnyException();
    assertThat(missionLogReactionRepository.count()).isEqualTo(1);
  }

  // 같은 member/log라도 다른 emoji token은 별도 row로 공존한다.
  @Test
  void upsertAllowsDifferentTokensForSameMemberAndLog() {
    missionLogReactionRepository.upsert(missionLogId, memberId, CLAP);
    missionLogReactionRepository.upsert(missionLogId, memberId, FIRE);

    assertThat(missionLogReactionRepository.findAll())
        .extracting(MissionLogReaction::getReactionType)
        .containsExactlyInAnyOrder(CLAP, FIRE);
  }

  // deleteReaction은 같은 token만 삭제하고 다른 token row는 유지한다.
  @Test
  void deleteReactionRemovesOnlyMatchingToken() {
    missionLogReactionRepository.upsert(missionLogId, memberId, CLAP);
    missionLogReactionRepository.upsert(missionLogId, memberId, FIRE);

    missionLogReactionRepository.deleteReaction(missionLogId, memberId, CLAP);

    assertThat(missionLogReactionRepository.findAll())
        .extracting(MissionLogReaction::getReactionType)
        .containsExactly(FIRE);
  }

  // 매칭 리액션이 없어도 deleteReaction은 0건 삭제로 정상 종료한다(멱등 삭제).
  @Test
  void deleteReactionIsIdempotentWhenAbsent() {
    assertThatCode(() -> missionLogReactionRepository.deleteReaction(missionLogId, memberId, CLAP))
        .doesNotThrowAnyException();
    assertThat(missionLogReactionRepository.count()).isZero();
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

  // MissionLog는 public 생성자/팩토리가 없어(엔티티 규칙) 리플렉션으로 필드를 채워 영속화한다.
  private MissionLog persistMissionLog(CrewParticipant participant) throws Exception {
    MissionLog log = newInstance(MissionLog.class);
    ReflectionTestUtils.setField(log, "crewParticipant", participant);
    ReflectionTestUtils.setField(log, "imageS3Key", "mission/log/reaction");
    ReflectionTestUtils.setField(log, "caption", "오늘도 인증 완료");
    ReflectionTestUtils.setField(log, "imageHash", "HASH_REACTION");
    ReflectionTestUtils.setField(log, "serverTime", LocalDateTime.of(2026, 6, 2, 8, 0));
    ReflectionTestUtils.setField(log, "exifRisk", ExifRisk.NORMAL);
    ReflectionTestUtils.setField(log, "certificationStatus", CertificationStatus.SUCCESS);
    return entityManager.persistAndFlush(log);
  }

  private static <T> T newInstance(Class<T> entityClass) throws Exception {
    Constructor<T> constructor = entityClass.getDeclaredConstructor();
    constructor.setAccessible(true);
    return constructor.newInstance();
  }
}
