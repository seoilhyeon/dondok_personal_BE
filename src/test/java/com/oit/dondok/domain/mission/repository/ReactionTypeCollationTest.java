package com.oit.dondok.domain.mission.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.IntegrationTest;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewNotice;
import com.oit.dondok.domain.crew.entity.CrewNoticeReaction;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.crew.repository.CrewNoticeReactionRepository;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.entity.CertificationStatus;
import com.oit.dondok.domain.mission.entity.ExifRisk;
import com.oit.dondok.domain.mission.entity.MissionLog;
import com.oit.dondok.domain.mission.entity.MissionLogReaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

// reaction_type collation 회귀 테스트.
// 이 버그는 Flyway 스키마의 utf8mb4_unicode_ci collation에서만 재현된다. 기존 reaction 테스트는
// integration 프로파일(create-drop, flyway off)이라 MySQL 서버 기본 collation을 써서 잡지 못한다.
// 따라서 Flyway를 켜고 실제 마이그레이션 스키마(V11 포함) 위에서 검증한다.
// V11 적용 전: 서로 다른 이모지가 유니크 제약에서 충돌해 1~2개만 저장 → 실패.
// V11 적용 후: utf8mb4_bin으로 구분되어 모두 저장 → 통과.
@IntegrationTest
@Tag("flyway")
@TestPropertySource(
    properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@Transactional
class ReactionTypeCollationTest {

  // utf8mb4_unicode_ci에서 동일 가중치로 비교되어 충돌하던 서로 다른 이모지들.
  private static final List<String> EMOJIS = List.of("👏", "🔥", "🎉", "😀");

  @PersistenceContext private EntityManager entityManager;
  @Autowired private MissionLogReactionRepository missionLogReactionRepository;
  @Autowired private MissionLogReactionQueryRepository missionLogReactionQueryRepository;
  @Autowired private CrewNoticeReactionRepository crewNoticeReactionRepository;

  private Member member;
  private MissionLog missionLog;
  private CrewNotice notice;

  @BeforeEach
  void setUp() throws Exception {
    member = persist(Member.create("reactor@example.com", "password-hash", "리액터"));
    Crew crew = persist(createCrew(member));
    CrewParticipant participant =
        persist(CrewParticipant.create(crew, member, 10_000L, LocalDateTime.of(2026, 5, 31, 9, 0)));
    missionLog = persist(createMissionLog(participant));
    notice = persist(CrewNotice.create(crew, member, "공지", "공지 내용"));
    entityManager.flush();
  }

  // 같은 미션 로그·회원이 서로 다른 이모지를 여러 개 달면 모두 저장되어야 한다.
  @Test
  void missionLogAllowsManyDistinctEmojiReactions() {
    EMOJIS.forEach(
        emoji -> missionLogReactionRepository.upsert(missionLog.getId(), member.getId(), emoji));

    assertThat(missionLogReactionRepository.findAll())
        .extracting(MissionLogReaction::getReactionType)
        .containsExactlyInAnyOrderElementsOf(EMOJIS);

    // reaction_type(bin) = 파라미터 비교 쿼리가 illegal mix of collations 없이 동작하고,
    // 해당 토큰만 정확히 삭제하는지 확인한다.
    missionLogReactionQueryRepository.deleteReaction(missionLog.getId(), member.getId(), "🔥");

    assertThat(missionLogReactionRepository.findAll())
        .extracting(MissionLogReaction::getReactionType)
        .containsExactlyInAnyOrder("👏", "🎉", "😀");
  }

  // 같은 공지·회원이 서로 다른 이모지를 여러 개 달면 모두 저장되어야 한다.
  @Test
  void crewNoticeAllowsManyDistinctEmojiReactions() {
    EMOJIS.forEach(
        emoji ->
            crewNoticeReactionRepository.save(CrewNoticeReaction.create(notice, member, emoji)));
    crewNoticeReactionRepository.flush();

    assertThat(crewNoticeReactionRepository.findByCrewNoticeId(notice.getId()))
        .extracting(CrewNoticeReaction::getReactionType)
        .containsExactlyInAnyOrderElementsOf(EMOJIS);

    // reaction_type(bin) = 파라미터 비교 파생 쿼리가 illegal mix of collations 없이 동작하는지 확인한다.
    assertThat(
            crewNoticeReactionRepository.findByCrewNoticeIdAndMemberIdAndReactionType(
                notice.getId(), member.getId(), "🎉"))
        .isPresent();
  }

  private <T> T persist(T entity) {
    entityManager.persist(entity);
    return entity;
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

  // MissionLog는 public 생성자/팩토리가 없어(엔티티 규칙) 리플렉션으로 필드를 채운다.
  private MissionLog createMissionLog(CrewParticipant participant) throws Exception {
    MissionLog log = newInstance(MissionLog.class);
    ReflectionTestUtils.setField(log, "crewParticipant", participant);
    ReflectionTestUtils.setField(log, "imageS3Key", "mission/log/reaction");
    ReflectionTestUtils.setField(log, "caption", "오늘도 인증 완료");
    ReflectionTestUtils.setField(log, "imageHash", "HASH_REACTION");
    ReflectionTestUtils.setField(log, "serverTime", LocalDateTime.of(2026, 6, 2, 8, 0));
    ReflectionTestUtils.setField(log, "exifRisk", ExifRisk.NORMAL);
    ReflectionTestUtils.setField(log, "certificationStatus", CertificationStatus.SUCCESS);
    return log;
  }

  private static <T> T newInstance(Class<T> type) throws Exception {
    Constructor<T> constructor = type.getDeclaredConstructor();
    constructor.setAccessible(true);
    return constructor.newInstance();
  }
}
