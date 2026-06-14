package com.oit.dondok.domain.crew.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.global.exception.CustomException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CrewTest {

  @Test
  void cancelChangesStatusToCancelled() {
    Crew crew = buildRecruitingCrew();
    LocalDateTime now = LocalDateTime.now();

    crew.cancel(now);

    assertThat(crew.getStatus()).isEqualTo(CrewStatus.CANCELLED);
    assertThat(crew.getCancelledAt()).isEqualTo(now);
  }

  @Test
  void cancelThrowsWhenStatusIsActive() {
    Crew crew = buildRecruitingCrew();
    crew.activate(LocalDateTime.now());

    assertThatThrownBy(() -> crew.cancel(LocalDateTime.now())).isInstanceOf(CustomException.class);
  }

  @Test
  void cancelThrowsWhenAlreadyCancelled() {
    Crew crew = buildRecruitingCrew();
    crew.cancel(LocalDateTime.now());

    assertThatThrownBy(() -> crew.cancel(LocalDateTime.now())).isInstanceOf(CustomException.class);
  }

  // ======================== disband ========================

  @Test
  void disbandFromRecruitingChangesStatusToCancelled() {
    Crew crew = buildRecruitingCrew();
    LocalDateTime now = LocalDateTime.now();

    crew.disband(now);

    assertThat(crew.getStatus()).isEqualTo(CrewStatus.CANCELLED);
    assertThat(crew.getCancelledAt()).isEqualTo(now);
  }

  @Test
  void disbandFromActiveChangesStatusToCancelled() {
    Crew crew = buildRecruitingCrew();
    crew.activate(LocalDateTime.now());
    LocalDateTime now = LocalDateTime.now();

    crew.disband(now);

    assertThat(crew.getStatus()).isEqualTo(CrewStatus.CANCELLED);
    assertThat(crew.getCancelledAt()).isEqualTo(now);
  }

  @Test
  void disbandThrowsWhenAlreadyCancelled() {
    Crew crew = buildRecruitingCrew();
    crew.disband(LocalDateTime.now());

    assertThatThrownBy(() -> crew.disband(LocalDateTime.now())).isInstanceOf(CustomException.class);
  }

  @Test
  void disbandThrowsWhenStatusIsClosed() {
    Crew crew = buildRecruitingCrew();
    ReflectionTestUtils.setField(crew, "status", CrewStatus.CLOSED);

    assertThatThrownBy(() -> crew.disband(LocalDateTime.now())).isInstanceOf(CustomException.class);
  }

  // ======================== helpers ========================

  private Crew buildRecruitingCrew() {
    LocalDateTime now = LocalDateTime.now();
    Crew crew =
        Crew.create(
            buildMember(),
            "테스트 크루",
            "크루 설명",
            null,
            "EXERCISE",
            "{}",
            HostPolicyVersion.HOST_POLICY_V1,
            now.minusDays(10),
            10_000L,
            2,
            5,
            now.minusDays(3),
            now.minusDays(1),
            now.plusDays(29));
    ReflectionTestUtils.setField(crew, "id", 1L);
    ReflectionTestUtils.setField(crew, "version", 0L);
    return crew;
  }

  private Member buildMember() {
    Member member = Member.create("test@example.com", "pw", "닉네임");
    ReflectionTestUtils.setField(member, "id", 1L);
    return member;
  }
}
