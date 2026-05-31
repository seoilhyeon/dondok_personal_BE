package com.oit.dondok.domain.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.config.JpaAuditingConfig;
import com.oit.dondok.config.QuerydslConfig;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.member.entity.Member;
import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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
@Import({JpaAuditingConfig.class, QuerydslConfig.class, MemberProfileQueryRepository.class})
class MemberProfileQueryRepositoryTest {

  @Autowired private TestEntityManager entityManager;
  @Autowired private MemberProfileQueryRepository memberProfileQueryRepository;

  @Test
  void findByMemberUuidReturnsProfileProjectionWithHostedCrewCount() throws Exception {
    Member member = persistMember("host@example.com", "호스트");
    Member anotherMember = persistMember("another@example.com", "다른호스트");
    ReflectionTestUtils.setField(member, "profileImageS3Key", "profile/member.png");
    ReflectionTestUtils.setField(member, "statusMessage", "돈독하게 습관 형성 중");

    entityManager.persist(newCrew(member, "아침 루틴"));
    entityManager.persist(newCrew(member, "저녁 루틴"));
    entityManager.persist(newCrew(anotherMember, "독서 루틴"));
    entityManager.flush();
    entityManager.clear();

    Optional<MemberProfileProjection> profile =
        memberProfileQueryRepository.findByMemberUuid(member.getUuid());

    assertThat(profile).isPresent();
    assertThat(profile.get().memberUuid()).isEqualTo(member.getUuid());
    assertThat(profile.get().email()).isEqualTo("host@example.com");
    assertThat(profile.get().nickname()).isEqualTo("호스트");
    assertThat(profile.get().profileImageS3Key()).isEqualTo("profile/member.png");
    assertThat(profile.get().statusMessage()).isEqualTo("돈독하게 습관 형성 중");
    assertThat(profile.get().hostedCrewCount()).isEqualTo(2L);
    assertThat(profile.get().isHostEver()).isTrue();
    assertThat(profile.get().status()).isEqualTo(member.getStatus());
    assertThat(profile.get().createdAt()).isInstanceOf(OffsetDateTime.class);
    assertThat(profile.get().createdAt().getOffset().getId()).isEqualTo("+09:00");
  }

  @Test
  void findByMemberUuidReturnsZeroHostedCrewCountWhenMemberHasNoHostedCrew() {
    Member member = persistMember("member@example.com", "일반회원");

    Optional<MemberProfileProjection> profile =
        memberProfileQueryRepository.findByMemberUuid(member.getUuid());

    assertThat(profile).isPresent();
    assertThat(profile.get().hostedCrewCount()).isZero();
    assertThat(profile.get().isHostEver()).isFalse();
  }

  @Test
  void findByMemberUuidReturnsEmptyWhenMemberDoesNotExist() {
    Optional<MemberProfileProjection> profile =
        memberProfileQueryRepository.findByMemberUuid(UUID.randomUUID());

    assertThat(profile).isEmpty();
  }

  private Member persistMember(String email, String nickname) {
    Member member = Member.create(email, "password-hash", nickname);

    return entityManager.persistAndFlush(member);
  }

  private static Crew newCrew(Member hostMember, String title) throws Exception {
    LocalDateTime now = LocalDateTime.of(2026, 5, 31, 9, 0);
    Crew crew = newInstance(Crew.class);

    ReflectionTestUtils.setField(crew, "hostMember", hostMember);
    ReflectionTestUtils.setField(crew, "title", title);
    ReflectionTestUtils.setField(crew, "description", title + " 설명");
    ReflectionTestUtils.setField(crew, "category", "HABIT");
    ReflectionTestUtils.setField(crew, "hostAgreementSnapshot", "{}");
    ReflectionTestUtils.setField(crew, "hostAgreementVersion", HostPolicyVersion.HOST_POLICY_V1);
    ReflectionTestUtils.setField(crew, "hostAgreedAt", now);
    ReflectionTestUtils.setField(crew, "status", CrewStatus.RECRUITING);
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
