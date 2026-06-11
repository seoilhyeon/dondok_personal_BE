package com.oit.dondok.domain.crew.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

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
  @Mock private CrewActivationProcessor crewActivationProcessor;

  @InjectMocks private CrewActivationBatchService crewActivationBatchService;

  @Test
  void activateCrewsDelegatesEachCrewToProcessor() {
    Crew crew1 = buildRecruitingCrew(1L);
    Crew crew2 = buildRecruitingCrew(2L);
    given(crewRepository.findByStatusAndStartAtBefore(eq(CrewStatus.RECRUITING), any()))
        .willReturn(List.of(crew1, crew2));

    crewActivationBatchService.activateCrews();

    then(crewActivationProcessor).should().processOne(eq(1L), any(LocalDateTime.class));
    then(crewActivationProcessor).should().processOne(eq(2L), any(LocalDateTime.class));
  }

  @Test
  void activateCrewsDoesNothingWhenNoTarget() {
    given(crewRepository.findByStatusAndStartAtBefore(any(), any())).willReturn(List.of());

    crewActivationBatchService.activateCrews();

    then(crewActivationProcessor).shouldHaveNoInteractions();
  }

  @Test
  void verifyRecruitingQueryIsUsed() {
    given(crewRepository.findByStatusAndStartAtBefore(any(), any())).willReturn(List.of());

    crewActivationBatchService.activateCrews();

    then(crewRepository)
        .should()
        .findByStatusAndStartAtBefore(eq(CrewStatus.RECRUITING), any(LocalDateTime.class));
  }

  @Test
  void continuesProcessingRemainingCrewsWhenOneProcessorFails() {
    Crew crew1 = buildRecruitingCrew(1L);
    Crew crew2 = buildRecruitingCrew(2L);
    given(crewRepository.findByStatusAndStartAtBefore(any(), any()))
        .willReturn(List.of(crew1, crew2));
    willThrow(new RuntimeException("처리 실패"))
        .given(crewActivationProcessor)
        .processOne(eq(1L), any());

    crewActivationBatchService.activateCrews();

    then(crewActivationProcessor).should().processOne(eq(1L), any(LocalDateTime.class));
    then(crewActivationProcessor).should().processOne(eq(2L), any(LocalDateTime.class));
  }

  // ======================== helpers ========================

  private Crew buildRecruitingCrew(Long id) {
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
            now.minusDays(1),
            now.plusDays(29));
    ReflectionTestUtils.setField(crew, "id", id);
    ReflectionTestUtils.setField(crew, "version", 0L);
    return crew;
  }

  private Member buildMember() {
    Member member = Member.create("test@example.com", "password-hash", "테스트닉네임");
    ReflectionTestUtils.setField(member, "id", 1L);
    return member;
  }
}
