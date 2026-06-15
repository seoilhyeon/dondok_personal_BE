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
import com.oit.dondok.domain.mission.entity.ExifRisk;
import com.oit.dondok.domain.mission.entity.MissionLog;
import com.oit.dondok.domain.mission.entity.ModerationDecisionType;
import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.Optional;
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
@Import({JpaAuditingConfig.class, QuerydslConfig.class, HostOperationQueryRepository.class})
class HostOperationQueryRepositoryTest {

  @Autowired private TestEntityManager entityManager;
  @Autowired private HostOperationQueryRepository hostOperationQueryRepository;

  @Test
  void existsByMemberUuidReturnsTrueWhenMemberExists() {
    Member member = persistMember("member@example.com", "회원");

    boolean exists = hostOperationQueryRepository.existsByMemberUuid(member.getUuid());

    assertThat(exists).isTrue();
  }

  @Test
  void existsByMemberUuidReturnsFalseWhenMemberDoesNotExist() {
    boolean exists = hostOperationQueryRepository.existsByMemberUuid(UUID.randomUUID());

    assertThat(exists).isFalse();
  }

  @Test
  void countTotalPendingOperationsByHostCountsOnlyCurrentHostPendingReviewsAndApplications()
      throws Exception {
    Member host = persistMember("host@example.com", "호스트");
    Member otherHost = persistMember("other-host@example.com", "다른호스트");
    Member member = persistMember("member@example.com", "회원");
    Member otherMember = persistMember("other-member@example.com", "다른회원");
    Member lockedApplicant = persistMember("locked@example.com", "확정회원");
    Member rejectedApplicant = persistMember("rejected@example.com", "거절회원");
    Member cancelledApplicant = persistMember("cancelled@example.com", "취소회원");
    Member expiredApplicant = persistMember("expired@example.com", "만료회원");

    Crew hostCrew = entityManager.persist(newCrew(host, "내 운영 크루", CrewStatus.ACTIVE));
    Crew hostRecruitingCrew =
        entityManager.persist(newCrew(host, "내 모집 크루", CrewStatus.RECRUITING));
    Crew otherHostCrew = entityManager.persist(newCrew(otherHost, "타인 운영 크루", CrewStatus.ACTIVE));

    CrewParticipant reviewParticipant =
        persistParticipant(hostCrew, member, CrewParticipantStatus.LOCKED, 1);
    persistMissionLog(reviewParticipant, CertificationStatus.PENDING_REVIEW, 1);
    persistMissionLog(reviewParticipant, CertificationStatus.PENDING_REVIEW, 2);
    persistMissionLog(reviewParticipant, CertificationStatus.SUCCESS, 3);
    persistMissionLog(reviewParticipant, CertificationStatus.FAILED, 4);

    CrewParticipant otherHostReviewParticipant =
        persistParticipant(otherHostCrew, otherMember, CrewParticipantStatus.LOCKED, 5);
    persistMissionLog(otherHostReviewParticipant, CertificationStatus.PENDING_REVIEW, 5);

    persistParticipant(hostRecruitingCrew, member, CrewParticipantStatus.PENDING, 6);
    persistParticipant(hostRecruitingCrew, otherMember, CrewParticipantStatus.PENDING, 7);
    persistParticipant(hostRecruitingCrew, lockedApplicant, CrewParticipantStatus.LOCKED, 8);
    persistParticipant(hostRecruitingCrew, rejectedApplicant, CrewParticipantStatus.REJECTED, 9);
    persistParticipant(hostRecruitingCrew, cancelledApplicant, CrewParticipantStatus.CANCELLED, 10);
    persistParticipant(hostRecruitingCrew, expiredApplicant, CrewParticipantStatus.EXPIRED, 11);
    persistParticipant(otherHostCrew, member, CrewParticipantStatus.PENDING, 12);
    entityManager.flush();
    entityManager.clear();

    long count = hostOperationQueryRepository.countTotalPendingOperationsByHost(host.getUuid());

    assertThat(count).isEqualTo(4L);
  }

  @Test
  void countTotalPendingOperationsByHostReturnsZeroWhenHostHasNoPendingOperations()
      throws Exception {
    Member host = persistMember("host@example.com", "호스트");
    Member member = persistMember("member@example.com", "회원");
    Member rejectedMember = persistMember("rejected@example.com", "거절회원");
    Crew crew = entityManager.persist(newCrew(host, "완료 크루", CrewStatus.ACTIVE));
    CrewParticipant participant = persistParticipant(crew, member, CrewParticipantStatus.LOCKED, 1);
    persistMissionLog(participant, CertificationStatus.SUCCESS, 1);
    persistParticipant(crew, rejectedMember, CrewParticipantStatus.REJECTED, 2);
    entityManager.flush();
    entityManager.clear();

    long count = hostOperationQueryRepository.countTotalPendingOperationsByHost(host.getUuid());

    assertThat(count).isZero();
  }

  // 대기 건수(검증 대기 + 가입 신청 대기) 합이 가장 많은 방장 크루를 선택한다.
  @Test
  void findDefaultHostCrewIdReturnsCrewWithMostPendingOperations() throws Exception {
    Member host = persistMember("host@example.com", "호스트");
    Member member1 = persistMember("member1@example.com", "회원1");
    Member member2 = persistMember("member2@example.com", "회원2");

    Crew lowCrew = entityManager.persist(newCrew(host, "대기 적은 크루", CrewStatus.ACTIVE));
    Crew highCrew = entityManager.persist(newCrew(host, "대기 많은 크루", CrewStatus.RECRUITING));

    // lowCrew: 검증 대기 1건
    CrewParticipant lowParticipant =
        persistParticipant(lowCrew, member1, CrewParticipantStatus.LOCKED, 1);
    persistMissionLog(lowParticipant, CertificationStatus.PENDING_REVIEW, 1);
    // highCrew: 가입 신청 대기 2건
    persistParticipant(highCrew, member1, CrewParticipantStatus.PENDING, 2);
    persistParticipant(highCrew, member2, CrewParticipantStatus.PENDING, 3);
    entityManager.flush();
    entityManager.clear();

    Optional<Long> result = hostOperationQueryRepository.findDefaultHostCrewId(host.getUuid());

    assertThat(result).contains(highCrew.getId());
  }

  // CANCELLED 크루는 대기가 많아도 제외하고, 대기 0건이라도 대상 방장 크루를 반환한다.
  @Test
  void findDefaultHostCrewIdExcludesCancelledAndReturnsZeroPendingCrew() throws Exception {
    Member host = persistMember("host@example.com", "호스트");
    Member member = persistMember("member@example.com", "회원");

    Crew cancelledCrew = entityManager.persist(newCrew(host, "취소 크루", CrewStatus.CANCELLED));
    persistParticipant(cancelledCrew, member, CrewParticipantStatus.PENDING, 1);
    Crew activeCrew = entityManager.persist(newCrew(host, "활성 크루", CrewStatus.ACTIVE));
    entityManager.flush();
    entityManager.clear();

    Optional<Long> result = hostOperationQueryRepository.findDefaultHostCrewId(host.getUuid());

    assertThat(result).contains(activeCrew.getId());
  }

  // 대기 건수 동률이면 가장 최근 생성 크루를 선택한다.
  @Test
  void findDefaultHostCrewIdPicksMostRecentlyCreatedOnTie() throws Exception {
    Member host = persistMember("host@example.com", "호스트");
    Crew olderCrew = entityManager.persist(newCrew(host, "오래된 크루", CrewStatus.ACTIVE));
    Crew newerCrew = entityManager.persist(newCrew(host, "최근 크루", CrewStatus.ACTIVE));
    entityManager.flush();
    // 둘 다 대기 0건 동률 → created_at을 명시 지정해 tie-break(최근 생성) 검증
    setCreatedAt(olderCrew.getId(), LocalDateTime.of(2026, 5, 1, 9, 0));
    setCreatedAt(newerCrew.getId(), LocalDateTime.of(2026, 5, 2, 9, 0));
    entityManager.clear();

    Optional<Long> result = hostOperationQueryRepository.findDefaultHostCrewId(host.getUuid());

    assertThat(result).contains(newerCrew.getId());
  }

  // 방장 크루가 없으면 empty.
  @Test
  void findDefaultHostCrewIdReturnsEmptyWhenNoHostCrew() {
    Member member = persistMember("member@example.com", "회원");

    Optional<Long> result = hostOperationQueryRepository.findDefaultHostCrewId(member.getUuid());

    assertThat(result).isEmpty();
  }

  // @CreatedDate 감사값을 덮어쓰기 위해 native update로 created_at을 명시 지정한다.
  private void setCreatedAt(Long crewId, LocalDateTime createdAt) {
    entityManager
        .getEntityManager()
        .createNativeQuery("UPDATE crew SET created_at = :createdAt WHERE id = :id")
        .setParameter("createdAt", createdAt)
        .setParameter("id", crewId)
        .executeUpdate();
  }

  private Member persistMember(String email, String nickname) {
    Member member = Member.create(email, "password-hash", nickname);

    return entityManager.persistAndFlush(member);
  }

  private CrewParticipant persistParticipant(
      Crew crew, Member member, CrewParticipantStatus status, int sequence) throws Exception {
    CrewParticipant participant = newInstance(CrewParticipant.class);

    ReflectionTestUtils.setField(participant, "crew", crew);
    ReflectionTestUtils.setField(participant, "member", member);
    ReflectionTestUtils.setField(participant, "status", status);
    ReflectionTestUtils.setField(participant, "depositAmount", 10_000L);
    ReflectionTestUtils.setField(
        participant, "pendingAt", LocalDateTime.of(2026, 5, 1, 9, 0).plusHours(sequence));
    if (status == CrewParticipantStatus.LOCKED) {
      ReflectionTestUtils.setField(
          participant, "lockedAt", LocalDateTime.of(2026, 5, 2, 9, 0).plusHours(sequence));
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
    ReflectionTestUtils.setField(missionLog, "exifRisk", ExifRisk.NORMAL);
    ReflectionTestUtils.setField(missionLog, "certificationStatus", certificationStatus);
    if (certificationStatus == CertificationStatus.FAILED) {
      ReflectionTestUtils.setField(missionLog, "duplicateHash", true);
      ReflectionTestUtils.setField(missionLog, "decisionType", ModerationDecisionType.AUTO_REJECT);
    }

    return entityManager.persist(missionLog);
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
    ReflectionTestUtils.setField(crew, "maxParticipants", 15);
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
