package com.oit.dondok.domain.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.config.JpaAuditingConfig;
import com.oit.dondok.config.QuerydslConfig;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.entity.CertificationStatus;
import com.oit.dondok.domain.mission.entity.MissionLog;
import com.oit.dondok.domain.notification.entity.Notification;
import com.oit.dondok.domain.settlement.entity.ParticipantStatusSnapshot;
import com.oit.dondok.domain.settlement.entity.RemainderPolicy;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementItem;
import com.oit.dondok.domain.settlement.entity.SettlementStatus;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@ActiveProfiles("test")
@DataJpaTest
@Import({JpaAuditingConfig.class, QuerydslConfig.class, MemberActivityQueryRepository.class})
class MemberActivityQueryRepositoryTest {

  @Autowired private TestEntityManager entityManager;
  @Autowired private MemberActivityQueryRepository memberActivityQueryRepository;

  @Test
  void existsByMemberUuidReturnsTrueWhenMemberExists() {
    Member member = persistMember("member@example.com", "회원");

    boolean exists = memberActivityQueryRepository.existsByMemberUuid(member.getUuid());

    assertThat(exists).isTrue();
  }

  @Test
  void existsByMemberUuidReturnsFalseWhenMemberDoesNotExist() {
    boolean exists = memberActivityQueryRepository.existsByMemberUuid(UUID.randomUUID());

    assertThat(exists).isFalse();
  }

  @Test
  void findCrewActivityInfoCountsOnlyLockedActiveAndCompletedCrewBuckets() throws Exception {
    Member member = persistMember("member@example.com", "회원");
    Member host = persistMember("host@example.com", "호스트");
    Member otherMember = persistMember("other@example.com", "다른회원");

    persistParticipant(
        newCrew(host, "모집중 확정 크루", CrewStatus.RECRUITING), member, CrewParticipantStatus.LOCKED);
    persistParticipant(
        newCrew(host, "진행중 확정 크루", CrewStatus.ACTIVE), member, CrewParticipantStatus.LOCKED);
    persistParticipant(
        newCrew(host, "종료 확정 크루", CrewStatus.CLOSED), member, CrewParticipantStatus.LOCKED);
    persistParticipant(
        newCrew(host, "모집중 신청 크루", CrewStatus.RECRUITING), member, CrewParticipantStatus.PENDING);
    persistParticipant(
        newCrew(host, "취소 확정 크루", CrewStatus.CANCELLED), member, CrewParticipantStatus.LOCKED);
    persistParticipant(
        newCrew(host, "거절된 진행 크루", CrewStatus.ACTIVE), member, CrewParticipantStatus.REJECTED);
    persistParticipant(
        newCrew(host, "타인 진행 크루", CrewStatus.ACTIVE), otherMember, CrewParticipantStatus.LOCKED);
    entityManager.flush();
    entityManager.clear();

    CrewActivityInfoProjection projection =
        memberActivityQueryRepository.findCrewActivityInfo(member.getUuid());

    assertThat(projection.totalCrewCount()).isEqualTo(3L);
    assertThat(projection.activeCrewCount()).isEqualTo(2L);
    assertThat(projection.completedCrewCount()).isEqualTo(1L);
  }

  @Test
  void countTotalVerificationCountsOnlyCurrentMemberMissionLogs() throws Exception {
    Member member = persistMember("member@example.com", "회원");
    Member otherMember = persistMember("other@example.com", "다른회원");
    Member host = persistMember("host@example.com", "호스트");
    Crew crew = entityManager.persist(newCrew(host, "인증 크루", CrewStatus.ACTIVE));
    CrewParticipant participant = persistParticipant(crew, member, CrewParticipantStatus.LOCKED);
    CrewParticipant otherParticipant =
        persistParticipant(crew, otherMember, CrewParticipantStatus.LOCKED);
    persistMissionLog(participant, CertificationStatus.SUCCESS, 1);
    persistMissionLog(participant, CertificationStatus.PENDING_REVIEW, 2);
    persistMissionLog(otherParticipant, CertificationStatus.SUCCESS, 3);
    entityManager.flush();
    entityManager.clear();

    long count = memberActivityQueryRepository.countTotalVerification(member.getUuid());

    assertThat(count).isEqualTo(2L);
  }

  @Test
  void countUnreadNotificationsCountsOnlyCurrentMemberUnreadNotifications() throws Exception {
    Member member = persistMember("member@example.com", "회원");
    Member otherMember = persistMember("other@example.com", "다른회원");
    persistNotification(member, null);
    persistNotification(member, null);
    persistNotification(member, LocalDateTime.of(2026, 6, 1, 10, 0));
    persistNotification(otherMember, null);
    entityManager.flush();
    entityManager.clear();

    long count = memberActivityQueryRepository.countUnreadNotifications(member.getUuid());

    assertThat(count).isEqualTo(2L);
  }

  @Test
  void findActivityStatsUsesSucceededSettlementItemsOnlyAndKeepsAverageSuccessRateNull()
      throws Exception {
    Member member = persistMember("member@example.com", "회원");
    Member otherMember = persistMember("other@example.com", "다른회원");
    Member host = persistMember("host@example.com", "호스트");

    Crew oldHighShareCrew = entityManager.persist(newCrew(host, "이전 최고 지분 크루", CrewStatus.CLOSED));
    Crew latestHighShareCrew =
        entityManager.persist(newCrew(host, "최신 최고 지분 크루", CrewStatus.CLOSED));
    Crew lowShareCrew = entityManager.persist(newCrew(host, "낮은 지분 크루", CrewStatus.CLOSED));
    Crew pendingSettlementCrew =
        entityManager.persist(newCrew(host, "미완료 정산 크루", CrewStatus.CLOSED));
    Crew otherMemberCrew = entityManager.persist(newCrew(host, "타인 정산 크루", CrewStatus.CLOSED));

    persistSettlementItem(
        member,
        persistParticipant(oldHighShareCrew, member, CrewParticipantStatus.LOCKED),
        persistSettlement(
            oldHighShareCrew, SettlementStatus.SUCCEEDED, LocalDateTime.of(2026, 5, 20, 12, 0)),
        10,
        new BigDecimal("0.300000"));
    persistSettlementItem(
        member,
        persistParticipant(latestHighShareCrew, member, CrewParticipantStatus.LOCKED),
        persistSettlement(
            latestHighShareCrew, SettlementStatus.SUCCEEDED, LocalDateTime.of(2026, 5, 31, 12, 0)),
        20,
        new BigDecimal("0.300000"));
    persistSettlementItem(
        member,
        persistParticipant(lowShareCrew, member, CrewParticipantStatus.LOCKED),
        persistSettlement(
            lowShareCrew, SettlementStatus.SUCCEEDED, LocalDateTime.of(2026, 5, 25, 12, 0)),
        5,
        new BigDecimal("0.100000"));
    persistSettlementItem(
        member,
        persistParticipant(pendingSettlementCrew, member, CrewParticipantStatus.LOCKED),
        persistSettlement(
            pendingSettlementCrew, SettlementStatus.PENDING, LocalDateTime.of(2026, 6, 1, 12, 0)),
        99,
        new BigDecimal("0.999999"));
    persistSettlementItem(
        otherMember,
        persistParticipant(otherMemberCrew, otherMember, CrewParticipantStatus.LOCKED),
        persistSettlement(
            otherMemberCrew, SettlementStatus.SUCCEEDED, LocalDateTime.of(2026, 6, 1, 12, 0)),
        77,
        new BigDecimal("0.999999"));
    entityManager.flush();
    entityManager.clear();

    ActivityStatsProjection projection =
        memberActivityQueryRepository.findActivityStats(member.getUuid());

    assertThat(projection.totalRecognizedSuccessCount()).isEqualTo(35L);
    assertThat(projection.highestShareRatio()).isEqualByComparingTo("0.300000");
    assertThat(projection.highestShareRatioCrewId()).isEqualTo(latestHighShareCrew.getId());
    assertThat(projection.highestShareRatioCrewTitle()).isEqualTo("최신 최고 지분 크루");
    assertThat(projection.averageSuccessRate()).isNull();
  }

  @Test
  void findActivityStatsReturnsZeroAndNullsWhenMemberHasNoSucceededSettlementItems() {
    Member member = persistMember("member@example.com", "회원");

    ActivityStatsProjection projection =
        memberActivityQueryRepository.findActivityStats(member.getUuid());

    assertThat(projection.totalRecognizedSuccessCount()).isZero();
    assertThat(projection.highestShareRatio()).isNull();
    assertThat(projection.highestShareRatioCrewId()).isNull();
    assertThat(projection.highestShareRatioCrewTitle()).isNull();
    assertThat(projection.averageSuccessRate()).isNull();
  }

  private Member persistMember(String email, String nickname) {
    Member member = Member.create(email, "password-hash", nickname);

    return entityManager.persistAndFlush(member);
  }

  private CrewParticipant persistParticipant(Crew crew, Member member, CrewParticipantStatus status)
      throws Exception {
    if (crew.getId() == null) {
      crew = entityManager.persist(crew);
    }
    CrewParticipant participant = newInstance(CrewParticipant.class);

    ReflectionTestUtils.setField(participant, "crew", crew);
    ReflectionTestUtils.setField(participant, "member", member);
    ReflectionTestUtils.setField(participant, "status", status);
    ReflectionTestUtils.setField(participant, "depositAmount", 10_000L);
    ReflectionTestUtils.setField(participant, "pendingAt", LocalDateTime.of(2026, 5, 1, 9, 0));
    if (status == CrewParticipantStatus.LOCKED) {
      ReflectionTestUtils.setField(participant, "lockedAt", LocalDateTime.of(2026, 5, 2, 9, 0));
    }

    return entityManager.persist(participant);
  }

  private MissionLog persistMissionLog(
      CrewParticipant participant, CertificationStatus certificationStatus, int dayOffset)
      throws Exception {
    MissionLog missionLog = newInstance(MissionLog.class);

    ReflectionTestUtils.setField(missionLog, "crewParticipant", participant);
    ReflectionTestUtils.setField(missionLog, "imageS3Key", "mission/test-" + dayOffset + ".jpg");
    ReflectionTestUtils.setField(missionLog, "caption", "오늘 인증 완료");
    ReflectionTestUtils.setField(
        missionLog, "serverTime", LocalDateTime.of(2026, 5, 10, 9, 0).plusDays(dayOffset));
    ReflectionTestUtils.setField(missionLog, "certificationStatus", certificationStatus);

    return entityManager.persist(missionLog);
  }

  private Notification persistNotification(Member member, LocalDateTime readAt) throws Exception {
    Notification notification = newInstance(Notification.class);

    ReflectionTestUtils.setField(notification, "uuid", UUID.randomUUID());
    ReflectionTestUtils.setField(notification, "member", member);
    ReflectionTestUtils.setField(notification, "eventType", "TEST_EVENT");
    ReflectionTestUtils.setField(notification, "resourceType", "crew");
    ReflectionTestUtils.setField(notification, "resourceId", "1");
    ReflectionTestUtils.setField(notification, "deepLink", "/crews/1");
    ReflectionTestUtils.setField(notification, "displayText", "테스트 알림");
    ReflectionTestUtils.setField(notification, "requiresRefetch", true);
    ReflectionTestUtils.setField(notification, "occurredAt", LocalDateTime.of(2026, 6, 1, 9, 0));
    ReflectionTestUtils.setField(notification, "readAt", readAt);

    return entityManager.persist(notification);
  }

  private Settlement persistSettlement(Crew crew, SettlementStatus status, LocalDateTime finishedAt)
      throws Exception {
    Settlement settlement = newInstance(Settlement.class);

    ReflectionTestUtils.setField(settlement, "crew", crew);
    ReflectionTestUtils.setField(settlement, "status", status);
    ReflectionTestUtils.setField(
        settlement, "baselineFrozenAt", LocalDateTime.of(2026, 5, 1, 9, 0));
    ReflectionTestUtils.setField(settlement, "retryCount", 0);
    ReflectionTestUtils.setField(settlement, "totalParticipants", 1);
    ReflectionTestUtils.setField(settlement, "totalLockedAmount", 10_000L);
    ReflectionTestUtils.setField(settlement, "totalRecognizedSuccess", 0);
    ReflectionTestUtils.setField(settlement, "totalBaseRefundAmount", 10_000L);
    ReflectionTestUtils.setField(settlement, "totalRemainderAmount", 0L);
    ReflectionTestUtils.setField(settlement, "remainderPolicy", RemainderPolicy.HOST_REMAINDER);
    ReflectionTestUtils.setField(settlement, "algorithmVersion", "test-v1");
    ReflectionTestUtils.setField(settlement, "ruleContextSnapshot", "{}");
    ReflectionTestUtils.setField(settlement, "finishedAt", finishedAt);

    return entityManager.persist(settlement);
  }

  private SettlementItem persistSettlementItem(
      Member member,
      CrewParticipant participant,
      Settlement settlement,
      int recognizedSuccessCount,
      BigDecimal shareRatio)
      throws Exception {
    SettlementItem settlementItem = newInstance(SettlementItem.class);

    ReflectionTestUtils.setField(settlementItem, "settlement", settlement);
    ReflectionTestUtils.setField(settlementItem, "crewParticipant", participant);
    ReflectionTestUtils.setField(settlementItem, "member", member);
    ReflectionTestUtils.setField(
        settlementItem, "participantStatusSnapshot", ParticipantStatusSnapshot.LOCKED);
    ReflectionTestUtils.setField(settlementItem, "depositAmount", 10_000L);
    ReflectionTestUtils.setField(settlementItem, "successCountRaw", recognizedSuccessCount);
    ReflectionTestUtils.setField(settlementItem, "recognizedSuccessCount", recognizedSuccessCount);
    ReflectionTestUtils.setField(settlementItem, "recognizedDatesCount", recognizedSuccessCount);
    ReflectionTestUtils.setField(settlementItem, "excludedSuccessCount", 0);
    ReflectionTestUtils.setField(
        settlementItem, "periodStartAt", LocalDateTime.of(2026, 5, 1, 0, 0));
    ReflectionTestUtils.setField(
        settlementItem, "periodEndAt", LocalDateTime.of(2026, 5, 31, 23, 59));
    ReflectionTestUtils.setField(settlementItem, "shareRatio", shareRatio);
    ReflectionTestUtils.setField(settlementItem, "baseRefundAmount", 10_000L);
    ReflectionTestUtils.setField(settlementItem, "remainderBonusAmount", 0L);
    ReflectionTestUtils.setField(settlementItem, "refundAmount", 10_000L);
    ReflectionTestUtils.setField(settlementItem, "calculationReason", "{}");

    return entityManager.persist(settlementItem);
  }

  private static Crew newCrew(Member hostMember, String title, CrewStatus status) throws Exception {
    LocalDateTime now = LocalDateTime.of(2026, 5, 1, 9, 0);
    Crew crew = newInstance(Crew.class);

    ReflectionTestUtils.setField(crew, "hostMember", hostMember);
    ReflectionTestUtils.setField(crew, "title", title);
    ReflectionTestUtils.setField(crew, "description", title + " 설명");
    ReflectionTestUtils.setField(crew, "category", "HABIT");
    ReflectionTestUtils.setField(crew, "hostAgreementSnapshot", "{}");
    ReflectionTestUtils.setField(crew, "hostAgreementVersion", HostPolicyVersion.HOST_POLICY_V1);
    ReflectionTestUtils.setField(crew, "hostAgreedAt", now);
    ReflectionTestUtils.setField(crew, "status", status);
    ReflectionTestUtils.setField(crew, "depositAmount", 10_000L);
    ReflectionTestUtils.setField(crew, "minParticipants", 2);
    ReflectionTestUtils.setField(crew, "maxParticipants", 5);
    ReflectionTestUtils.setField(crew, "recruitmentDeadline", now.plusDays(3));
    ReflectionTestUtils.setField(crew, "startAt", now.plusDays(4));
    ReflectionTestUtils.setField(crew, "endAt", now.plusDays(30));

    return crew;
  }

  private static <T> T newInstance(Class<T> entityClass) throws Exception {
    Constructor<T> constructor = entityClass.getDeclaredConstructor();
    constructor.setAccessible(true);

    return constructor.newInstance();
  }
}
