package com.oit.dondok.domain.crew.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.config.JpaAuditingConfig;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.member.entity.Member;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest(
    properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(JpaAuditingConfig.class)
class CrewParticipantRepositoryTest {

  private static final LocalDateTime BASE = LocalDateTime.of(2026, 6, 10, 0, 0);

  @Autowired private CrewParticipantRepository crewParticipantRepository;
  @Autowired private TestEntityManager em;

  @Test
  void findIncludesParticipantWhenDeadlineEqualsQueryTime() {
    Member member = em.persist(member("a@a.com"));
    Crew crew = em.persist(crew(member, BASE));
    CrewParticipant participant = em.persist(pending(crew, member));
    em.flush();

    List<CrewParticipant> result =
        crewParticipantRepository.findByStatusAndCrewStatusAndCrewRecruitmentDeadlineLessThanEqual(
            CrewParticipantStatus.PENDING, CrewStatus.RECRUITING, BASE);

    assertThat(result).extracting(CrewParticipant::getId).containsExactly(participant.getId());
  }

  @Test
  void findIncludesParticipantWhenDeadlineIsBeforeQueryTime() {
    Member member = em.persist(member("b@b.com"));
    Crew crew = em.persist(crew(member, BASE.minusSeconds(1)));
    CrewParticipant participant = em.persist(pending(crew, member));
    em.flush();

    List<CrewParticipant> result =
        crewParticipantRepository.findByStatusAndCrewStatusAndCrewRecruitmentDeadlineLessThanEqual(
            CrewParticipantStatus.PENDING, CrewStatus.RECRUITING, BASE);

    assertThat(result).extracting(CrewParticipant::getId).containsExactly(participant.getId());
  }

  @Test
  void findExcludesParticipantWhenDeadlineIsAfterQueryTime() {
    Member member = em.persist(member("c@c.com"));
    Crew crew = em.persist(crew(member, BASE.plusSeconds(1)));
    CrewParticipant participant = em.persist(pending(crew, member));
    em.flush();

    List<CrewParticipant> result =
        crewParticipantRepository.findByStatusAndCrewStatusAndCrewRecruitmentDeadlineLessThanEqual(
            CrewParticipantStatus.PENDING, CrewStatus.RECRUITING, BASE);

    assertThat(result).extracting(CrewParticipant::getId).doesNotContain(participant.getId());
  }

  @Test
  void findExcludesNonPendingParticipant() {
    Member member = em.persist(member("d@d.com"));
    Crew crew = em.persist(crew(member, BASE.minusDays(1)));
    CrewParticipant participant = em.persist(pending(crew, member));
    participant.cancel(BASE);
    em.flush();

    List<CrewParticipant> result =
        crewParticipantRepository.findByStatusAndCrewStatusAndCrewRecruitmentDeadlineLessThanEqual(
            CrewParticipantStatus.PENDING, CrewStatus.RECRUITING, BASE);

    assertThat(result).extracting(CrewParticipant::getId).doesNotContain(participant.getId());
  }

  @Test
  void findExcludesParticipantInNonRecruitingCrew() {
    Member member = em.persist(member("e@e.com"));
    Crew crew = em.persist(crew(member, BASE.minusDays(1)));
    crew.activate(BASE);
    CrewParticipant participant = em.persist(pending(crew, member));
    em.flush();

    List<CrewParticipant> result =
        crewParticipantRepository.findByStatusAndCrewStatusAndCrewRecruitmentDeadlineLessThanEqual(
            CrewParticipantStatus.PENDING, CrewStatus.RECRUITING, BASE);

    assertThat(result).extracting(CrewParticipant::getId).doesNotContain(participant.getId());
  }

  // ======================== helpers 보조 메서드 ========================

  private Member member(String email) {
    return Member.create(email, "pw-hash", "닉네임");
  }

  private Crew crew(Member host, LocalDateTime recruitmentDeadline) {
    return Crew.create(
        host,
        "테스트 크루",
        "크루 설명",
        null,
        "EXERCISE",
        "{}",
        HostPolicyVersion.HOST_POLICY_V1,
        LocalDateTime.now(),
        10_000L,
        2,
        5,
        recruitmentDeadline,
        recruitmentDeadline.plusDays(5),
        recruitmentDeadline.plusDays(35));
  }

  private CrewParticipant pending(Crew crew, Member member) {
    return CrewParticipant.createPending(crew, member, 10_000L, LocalDateTime.now());
  }
}
