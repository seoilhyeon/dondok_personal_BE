package com.oit.dondok.domain.crew.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CrewActivationProcessorTest {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
  private static final List<CrewParticipantStatus> REFUNDABLE =
      List.of(CrewParticipantStatus.LOCKED, CrewParticipantStatus.PENDING);

  @Mock private CrewRepository crewRepository;
  @Mock private CrewParticipantRepository crewParticipantRepository;
  @Mock private CrewPointPort crewPointPort;

  @InjectMocks private CrewActivationProcessor crewActivationProcessor;

  @Test
  void activatesCrewWhenLockedCountMeetsMinParticipants() {
    Crew crew = buildRecruitingCrew(1L, 2);
    given(crewRepository.findById(1L)).willReturn(Optional.of(crew));
    given(crewParticipantRepository.findByCrewIdAndStatusIn(eq(1L), eq(REFUNDABLE)))
        .willReturn(List.of(buildLockedParticipant(10L), buildLockedParticipant(11L)));

    crewActivationProcessor.processOne(1L, LocalDateTime.now(SEOUL_ZONE));

    assertThat(crew.getStatus()).isEqualTo(CrewStatus.ACTIVE);
    assertThat(crew.getActivatedAt()).isNotNull();
    then(crewPointPort).should(never()).releaseLockedDepositForCancelledCrew(any());
    then(crewPointPort).should(never()).releasePendingReserve(any());
  }

  @Test
  void cancelsCrewAndRefundsLockedParticipantsWhenBelowMin() {
    Crew crew = buildRecruitingCrew(1L, 3);
    CrewParticipant locked1 = buildLockedParticipant(10L);
    CrewParticipant locked2 = buildLockedParticipant(11L);
    given(crewRepository.findById(1L)).willReturn(Optional.of(crew));
    given(crewParticipantRepository.findByCrewIdAndStatusIn(eq(1L), eq(REFUNDABLE)))
        .willReturn(List.of(locked1, locked2));

    crewActivationProcessor.processOne(1L, LocalDateTime.now(SEOUL_ZONE));

    assertThat(crew.getStatus()).isEqualTo(CrewStatus.CANCELLED);
    assertThat(locked1.getStatus()).isEqualTo(CrewParticipantStatus.CANCELLED);
    assertThat(locked2.getStatus()).isEqualTo(CrewParticipantStatus.CANCELLED);
    then(crewPointPort).should().releaseLockedDepositForCancelledCrew(locked1);
    then(crewPointPort).should().releaseLockedDepositForCancelledCrew(locked2);
    then(crewPointPort).should(never()).releasePendingReserve(any());
  }

  @Test
  void cancelsCrewAndRefundsPendingParticipantsWhenBelowMin() {
    Crew crew = buildRecruitingCrew(1L, 3);
    CrewParticipant pending1 = buildPendingParticipant(20L);
    CrewParticipant pending2 = buildPendingParticipant(21L);
    given(crewRepository.findById(1L)).willReturn(Optional.of(crew));
    given(crewParticipantRepository.findByCrewIdAndStatusIn(eq(1L), eq(REFUNDABLE)))
        .willReturn(List.of(pending1, pending2));

    crewActivationProcessor.processOne(1L, LocalDateTime.now(SEOUL_ZONE));

    assertThat(crew.getStatus()).isEqualTo(CrewStatus.CANCELLED);
    assertThat(pending1.getStatus()).isEqualTo(CrewParticipantStatus.CANCELLED);
    assertThat(pending2.getStatus()).isEqualTo(CrewParticipantStatus.CANCELLED);
    then(crewPointPort).should().releasePendingReserve(pending1);
    then(crewPointPort).should().releasePendingReserve(pending2);
    then(crewPointPort).should(never()).releaseLockedDepositForCancelledCrew(any());
  }

  @Test
  void cancelsCrewAndRefundsBothLockedAndPendingParticipants() {
    Crew crew = buildRecruitingCrew(1L, 5);
    CrewParticipant locked = buildLockedParticipant(10L);
    CrewParticipant pending = buildPendingParticipant(20L);
    given(crewRepository.findById(1L)).willReturn(Optional.of(crew));
    given(crewParticipantRepository.findByCrewIdAndStatusIn(eq(1L), eq(REFUNDABLE)))
        .willReturn(List.of(locked, pending));

    crewActivationProcessor.processOne(1L, LocalDateTime.now(SEOUL_ZONE));

    assertThat(crew.getStatus()).isEqualTo(CrewStatus.CANCELLED);
    assertThat(locked.getStatus()).isEqualTo(CrewParticipantStatus.CANCELLED);
    assertThat(pending.getStatus()).isEqualTo(CrewParticipantStatus.CANCELLED);
    then(crewPointPort).should().releaseLockedDepositForCancelledCrew(locked);
    then(crewPointPort).should().releasePendingReserve(pending);
  }

  @Test
  void queriesParticipantsExactlyOnceRegardlessOfOutcome() {
    Crew crew = buildRecruitingCrew(1L, 2);
    given(crewRepository.findById(1L)).willReturn(Optional.of(crew));
    given(crewParticipantRepository.findByCrewIdAndStatusIn(eq(1L), eq(REFUNDABLE)))
        .willReturn(List.of(buildLockedParticipant(10L), buildLockedParticipant(11L)));

    crewActivationProcessor.processOne(1L, LocalDateTime.now(SEOUL_ZONE));

    then(crewParticipantRepository).should().findByCrewIdAndStatusIn(any(), any());
    then(crewParticipantRepository).shouldHaveNoMoreInteractions();
  }

  // ======================== helpers ========================

  private Crew buildRecruitingCrew(Long id, int minParticipants) {
    LocalDateTime now = LocalDateTime.now(SEOUL_ZONE);
    Crew crew =
        Crew.create(
            buildMember(1L),
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
            now.minusDays(1),
            now.plusDays(29));
    ReflectionTestUtils.setField(crew, "id", id);
    ReflectionTestUtils.setField(crew, "version", 0L);
    return crew;
  }

  private CrewParticipant buildLockedParticipant(Long id) {
    CrewParticipant p =
        CrewParticipant.create(
            buildRecruitingCrew(1L, 2), buildMember(id), 10_000L, LocalDateTime.now());
    ReflectionTestUtils.setField(p, "id", id);
    ReflectionTestUtils.setField(p, "version", 0L);
    return p;
  }

  private CrewParticipant buildPendingParticipant(Long id) {
    CrewParticipant p =
        CrewParticipant.createPending(
            buildRecruitingCrew(1L, 2), buildMember(id), 10_000L, LocalDateTime.now());
    ReflectionTestUtils.setField(p, "id", id);
    ReflectionTestUtils.setField(p, "version", 0L);
    return p;
  }

  private Member buildMember(Long id) {
    Member member = Member.create("member" + id + "@example.com", "pw", "닉네임" + id);
    ReflectionTestUtils.setField(member, "id", id);
    return member;
  }
}
