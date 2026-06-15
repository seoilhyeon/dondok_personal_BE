package com.oit.dondok.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.IntegrationTest;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.dto.response.ReactionResponse;
import com.oit.dondok.domain.mission.entity.CertificationStatus;
import com.oit.dondok.domain.mission.entity.ExifRisk;
import com.oit.dondok.domain.mission.entity.MissionLog;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// addReaction의 read-after-write 재현 테스트.
// 런타임과 동일하게 서비스가 자체 트랜잭션에서 돌도록 테스트 메서드는 비트랜잭션으로 둔다(부모 데이터는 별도 tx로 커밋).
// addReaction은 upsert 직후 같은 호출 안에서 집계를 다시 읽어 응답으로 돌려준다.
// 응답에 방금 추가한 리액션이 비어 있으면(=한 박자 밀림) 여기서 실패한다.
@IntegrationTest
@Tag("flyway")
@TestPropertySource(
    properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
class ReactionReadAfterWriteTest {

  @Autowired private MissionLogReactionService missionLogReactionService;
  @Autowired private PlatformTransactionManager transactionManager;
  @PersistenceContext private EntityManager entityManager;

  private UUID memberUuid;
  private Long missionLogId;

  @BeforeEach
  void setUp() throws Exception {
    Member member = Member.create("readafterwrite@example.com", "password-hash", "RAW리액터");
    Crew crew = createCrew(member);
    CrewParticipant participant =
        CrewParticipant.create(crew, member, 10_000L, LocalDateTime.of(2026, 5, 31, 9, 0));
    MissionLog missionLog = createMissionLog(participant);

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              entityManager.persist(member);
              entityManager.persist(crew);
              entityManager.persist(participant);
              entityManager.persist(missionLog);
              entityManager.flush();
            });

    this.memberUuid = member.getUuid();
    this.missionLogId = missionLog.getId();
  }

  @Test
  void responseReflectsJustAddedReaction() {
    ReactionResponse first = missionLogReactionService.addReaction(memberUuid, missionLogId, "👏");
    assertThat(first.myReactions()).containsExactly("👏");
    assertThat(first.reactionCounts()).containsEntry("👏", 1L);

    ReactionResponse second = missionLogReactionService.addReaction(memberUuid, missionLogId, "🔥");
    assertThat(second.myReactions()).containsExactlyInAnyOrder("👏", "🔥");
    assertThat(second.reactionCounts()).containsEntry("👏", 1L).containsEntry("🔥", 1L);
  }

  private Crew createCrew(Member host) {
    LocalDateTime now = LocalDateTime.of(2026, 5, 31, 9, 0);
    return Crew.create(
        host,
        "크루",
        "크루 설명",
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
  }

  private MissionLog createMissionLog(CrewParticipant participant) throws Exception {
    Constructor<MissionLog> constructor = MissionLog.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    MissionLog log = constructor.newInstance();
    ReflectionTestUtils.setField(log, "crewParticipant", participant);
    ReflectionTestUtils.setField(log, "imageS3Key", "mission/log/reaction");
    ReflectionTestUtils.setField(log, "caption", "오늘도 인증 완료");
    ReflectionTestUtils.setField(log, "imageHash", "HASH_REACTION");
    ReflectionTestUtils.setField(log, "serverTime", LocalDateTime.of(2026, 6, 2, 8, 0));
    ReflectionTestUtils.setField(log, "exifRisk", ExifRisk.NORMAL);
    ReflectionTestUtils.setField(log, "certificationStatus", CertificationStatus.SUCCESS);
    return log;
  }
}
