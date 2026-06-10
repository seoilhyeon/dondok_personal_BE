package com.oit.dondok.domain.crew.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.crew.port.CrewPointPort;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
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
  @Mock private CrewParticipantRepository crewParticipantRepository;
  @Mock private CrewPointPort crewPointPort;

  @InjectMocks private CrewActivationBatchService crewActivationBatchService;

  @Test
  void activateCrewsActivatesRecruitingCrewsPastStartAt() {
    // given
    Crew crew1 = buildRecruitingCrew(1L, 2);
    Crew crew2 = buildRecruitingCrew(2L, 2);
    given(
            crewRepository.findByStatusAndStartAtBefore(
                eq(CrewStatus.RECRUITING), any(LocalDateTime.class)))
        .willReturn(List.of(crew1, crew2));
    given(
            crewParticipantRepository.countByCrewIdAndStatus(
                eq(1L), eq(CrewParticipantStatus.LOCKED)))
        .willReturn(2L);
    given(
            crewParticipantRepository.countByCrewIdAndStatus(
                eq(2L), eq(CrewParticipantStatus.LOCKED)))
        .willReturn(3L);

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
  void verifyRecruitingQueryIsUsed() {
    // given
    given(crewRepository.findByStatusAndStartAtBefore(any(), any())).willReturn(List.of());

    // when
    crewActivationBatchService.activateCrews();

    // then: 쿼리는 반드시 RECRUITING 상태만 대상으로 한다 — 다른 상태 크루는 조회 자체에서 걸러진다
    then(crewRepository)
        .should()
        .findByStatusAndStartAtBefore(eq(CrewStatus.RECRUITING), any(LocalDateTime.class));
  }

  @Test
  void cancelsCrewWhenLockedCountBelowMinParticipants() {
    // given
    Crew crew = buildRecruitingCrew(1L, 3);
    CrewParticipant p1 = mock(CrewParticipant.class);
    CrewParticipant p2 = mock(CrewParticipant.class);

    given(crewRepository.findByStatusAndStartAtBefore(any(), any())).willReturn(List.of(crew));
    given(crewParticipantRepository.countByCrewIdAndStatus(1L, CrewParticipantStatus.LOCKED))
        .willReturn(2L);
    given(crewParticipantRepository.findByCrewIdAndStatus(1L, CrewParticipantStatus.LOCKED))
        .willReturn(List.of(p1, p2));

    // when
    crewActivationBatchService.activateCrews();

    // then
    assertThat(crew.getStatus()).isEqualTo(CrewStatus.CANCELLED);
    then(crewPointPort).should().releaseLockedDepositForCancelledCrew(p1);
    then(crewPointPort).should().releaseLockedDepositForCancelledCrew(p2);
  }

  @Test
  void activatesCrewWhenLockedCountMeetsMinParticipants() {
    // given
    Crew crew = buildRecruitingCrew(1L, 2);
    given(crewRepository.findByStatusAndStartAtBefore(any(), any())).willReturn(List.of(crew));
    given(crewParticipantRepository.countByCrewIdAndStatus(1L, CrewParticipantStatus.LOCKED))
        .willReturn(2L);

    // when
    crewActivationBatchService.activateCrews();

    // then
    assertThat(crew.getStatus()).isEqualTo(CrewStatus.ACTIVE);
    then(crewPointPort).should(never()).releaseLockedDepositForCancelledCrew(any());
  }

  @Test
  void cancelsCrewWithNoLockedParticipants() {
    // given: 모집 기간이 지났지만 LOCKED 참여자가 한 명도 없는 크루
    Crew crew = buildRecruitingCrew(1L, 2);
    given(crewRepository.findByStatusAndStartAtBefore(any(), any())).willReturn(List.of(crew));
    given(crewParticipantRepository.countByCrewIdAndStatus(1L, CrewParticipantStatus.LOCKED))
        .willReturn(0L);
    given(crewParticipantRepository.findByCrewIdAndStatus(1L, CrewParticipantStatus.LOCKED))
        .willReturn(List.of());

    // when
    crewActivationBatchService.activateCrews();

    // then
    assertThat(crew.getStatus()).isEqualTo(CrewStatus.CANCELLED);
    then(crewPointPort).should(never()).releaseLockedDepositForCancelledCrew(any());
  }

  @Test
  void processesActivationAndCancellationInSameBatch() {
    // given: 인원 충족 크루와 미달 크루가 동시에 처리되는 경우
    Crew crewToActivate = buildRecruitingCrew(1L, 2);
    Crew crewToCancel = buildRecruitingCrew(2L, 3);
    CrewParticipant participant = mock(CrewParticipant.class);

    given(crewRepository.findByStatusAndStartAtBefore(any(), any()))
        .willReturn(List.of(crewToActivate, crewToCancel));
    given(crewParticipantRepository.countByCrewIdAndStatus(1L, CrewParticipantStatus.LOCKED))
        .willReturn(2L);
    given(crewParticipantRepository.countByCrewIdAndStatus(2L, CrewParticipantStatus.LOCKED))
        .willReturn(1L);
    given(crewParticipantRepository.findByCrewIdAndStatus(2L, CrewParticipantStatus.LOCKED))
        .willReturn(List.of(participant));

    // when
    crewActivationBatchService.activateCrews();

    // then
    assertThat(crewToActivate.getStatus()).isEqualTo(CrewStatus.ACTIVE);
    assertThat(crewToCancel.getStatus()).isEqualTo(CrewStatus.CANCELLED);
    then(crewPointPort).should().releaseLockedDepositForCancelledCrew(participant);
    then(crewPointPort).shouldHaveNoMoreInteractions();
  }

  // ======================== helpers ========================

  private Member buildMember() {
    Member member = Member.create("test@example.com", "password-hash", "테스트닉네임");
    ReflectionTestUtils.setField(member, "id", 1L);
    return member;
  }

  private Crew buildRecruitingCrew(Long id, int minParticipants) {
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
            minParticipants,
            5,
            now.minusDays(3),
            now.minusDays(1), // start_at 과거 → 활성화 대상
            now.plusDays(29));
    ReflectionTestUtils.setField(crew, "id", id);
    ReflectionTestUtils.setField(crew, "version", 0L);
    return crew;
  }
}
