package com.oit.dondok.domain.crew.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import com.oit.dondok.domain.member.entity.Member;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CrewActivationBatchServiceTest {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

  @Mock private CrewRepository crewRepository;

  @InjectMocks private CrewActivationBatchService crewActivationBatchService;

  @Test
  void activateCrewsActivatesRecruitingCrewsPastStartAt() {
    // given
    Crew crew1 = buildRecruitingCrew();
    Crew crew2 = buildRecruitingCrew();
    given(
            crewRepository.findByStatusAndStartAtBefore(
                eq(CrewStatus.RECRUITING), any(LocalDateTime.class)))
        .willReturn(List.of(crew1, crew2));

    // when
    crewActivationBatchService.activateCrews();

    // then
    assertThat(crew1.getStatus()).isEqualTo(CrewStatus.ACTIVE);
    assertThat(crew1.getActivatedAt()).isNotNull();
    assertThat(crew2.getStatus()).isEqualTo(CrewStatus.ACTIVE);
    assertThat(crew2.getActivatedAt()).isNotNull();
  }

  @Test
  void activateCrewsDoesNothingWhenNoTarget() {
    // given
    given(crewRepository.findByStatusAndStartAtBefore(any(), any())).willReturn(List.of());

    // when
    crewActivationBatchService.activateCrews();

    // then: 조회는 수행되지만 빈 결과로 인해 크루 상태 변경이 발생하지 않는다
    then(crewRepository).should().findByStatusAndStartAtBefore(any(), any());
    then(crewRepository).shouldHaveNoMoreInteractions();
  }

  @Test
  void activateCrewsSkipsAlreadyActiveCrews() {
    // given: 이미 ACTIVE인 크루는 RECRUITING 조건 조회에서 제외된다
    Crew activeCrew = buildActiveCrew();
    given(
            crewRepository.findByStatusAndStartAtBefore(
                eq(CrewStatus.RECRUITING), any(LocalDateTime.class)))
        .willReturn(List.of());

    // when
    crewActivationBatchService.activateCrews();

    // then: ACTIVE 크루의 status는 변경되지 않는다
    assertThat(activeCrew.getStatus()).isEqualTo(CrewStatus.ACTIVE);
    then(crewRepository)
        .should()
        .findByStatusAndStartAtBefore(eq(CrewStatus.RECRUITING), any(LocalDateTime.class));
  }

  // ======================== helpers ========================

  private Member buildMember() {
    Member member = Member.create("test@example.com", "password-hash", "테스트닉네임");
    ReflectionTestUtils.setField(member, "id", 1L);
    return member;
  }

  private Crew buildRecruitingCrew() {
    LocalDateTime now = LocalDateTime.now(SEOUL_ZONE);
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
            now.minusDays(1), // start_at 과거 → 활성화 대상
            now.plusDays(29));
    ReflectionTestUtils.setField(crew, "id", 1L);
    ReflectionTestUtils.setField(crew, "version", 0L);
    return crew;
  }

  private Crew buildActiveCrew() {
    Crew crew = buildRecruitingCrew();
    ReflectionTestUtils.setField(crew, "status", CrewStatus.ACTIVE);
    return crew;
  }
}
