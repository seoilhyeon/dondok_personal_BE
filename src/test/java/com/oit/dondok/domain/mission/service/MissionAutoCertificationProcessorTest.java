package com.oit.dondok.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.domain.mission.entity.CertificationStatus;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.ExifRisk;
import com.oit.dondok.domain.mission.entity.MissionFailureReason;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.mission.entity.MissionLog;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.entity.ModerationDecisionType;
import com.oit.dondok.domain.mission.entity.ModerationHistory;
import com.oit.dondok.domain.mission.repository.MissionLogQueryRepository;
import com.oit.dondok.domain.mission.repository.MissionRuleRepository;
import com.oit.dondok.domain.mission.repository.ModerationHistoryRepository;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MissionAutoCertificationProcessorTest {

  private static final Long MISSION_LOG_ID = 1001L;
  private static final Long CREW_ID = 42L;
  private static final Long SYSTEM_MEMBER_ID = 999L;
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 10, 12, 0);

  @Mock private MissionLogQueryRepository missionLogQueryRepository;
  @Mock private MissionRuleRepository missionRuleRepository;
  @Mock private SettlementRepository settlementRepository;
  @Mock private ModerationHistoryRepository moderationHistoryRepository;
  @Mock private MemberRepository memberRepository;
  @Mock private SystemMemberProvider systemMemberProvider;
  @Mock private AutoCertificationDecider autoCertificationDecider;

  private MissionAutoCertificationProcessor processor;

  @BeforeEach
  void setUp() {
    processor =
        new MissionAutoCertificationProcessor(
            missionLogQueryRepository,
            missionRuleRepository,
            settlementRepository,
            moderationHistoryRepository,
            memberRepository,
            systemMemberProvider,
            autoCertificationDecider,
            new ObjectMapper().registerModule(new JavaTimeModule()));
  }

  // 자동 승인 판정이면 MissionLog를 SUCCESS/AUTO_APPROVE로 바꾸고 이력을 남긴다.
  @Test
  void approveDuePendingReviewLogAutomatically() {
    MissionLog missionLog = pendingReviewLog();
    givenAutoCertificationContext(missionLog);
    given(autoCertificationDecider.decide(missionLog))
        .willReturn(AutoCertificationDecision.approve());

    processor.confirmOne(MISSION_LOG_ID, NOW);

    assertThat(missionLog.getCertificationStatus()).isEqualTo(CertificationStatus.SUCCESS);
    assertThat(missionLog.getDecisionType()).isEqualTo(ModerationDecisionType.AUTO_APPROVE);
    assertThat(missionLog.getFailureReason()).isNull();
    assertThat(missionLog.getRejectReasonCode()).isNull();
    assertThat(missionLog.getRejectMemo()).isNull();
    assertThat(missionLog.getModerator().getId()).isEqualTo(SYSTEM_MEMBER_ID);
    verify(moderationHistoryRepository).save(any(ModerationHistory.class));
  }

  // 자동 반려 판정이면 MissionLog를 FAILED/AUTO_REJECT로 바꾸고 실패 사유를 남긴다.
  @Test
  void rejectDuePendingReviewLogAutomatically() {
    MissionLog missionLog = pendingReviewLog();
    givenAutoCertificationContext(missionLog);
    given(autoCertificationDecider.decide(missionLog))
        .willReturn(AutoCertificationDecision.reject(MissionFailureReason.DUPLICATE_IMAGE_HASH));
    ArgumentCaptor<ModerationHistory> historyCaptor =
        ArgumentCaptor.forClass(ModerationHistory.class);

    processor.confirmOne(MISSION_LOG_ID, NOW);

    assertThat(missionLog.getCertificationStatus()).isEqualTo(CertificationStatus.FAILED);
    assertThat(missionLog.getDecisionType()).isEqualTo(ModerationDecisionType.AUTO_REJECT);
    assertThat(missionLog.getFailureReason()).isEqualTo(MissionFailureReason.DUPLICATE_IMAGE_HASH);
    assertThat(missionLog.getRejectReasonCode()).isNull();
    assertThat(missionLog.getRejectMemo()).isNull();

    verify(moderationHistoryRepository).save(historyCaptor.capture());
    assertThat(historyCaptor.getValue().getDecisionType())
        .isEqualTo(ModerationDecisionType.AUTO_REJECT);
  }

  // 아직 타입별 자동 인증 시각이 되지 않은 로그는 상태 변경 없이 건너뛴다.
  @Test
  void skipWhenAutoCertificationTimeIsNotDue() {
    MissionLog missionLog = pendingReviewLog();
    given(missionLogQueryRepository.findByIdWithCrewForAutoCertification(MISSION_LOG_ID))
        .willReturn(Optional.of(missionLog));
    given(settlementRepository.findByCrewId(CREW_ID)).willReturn(Optional.empty());
    given(missionRuleRepository.findByCrewId(CREW_ID))
        .willReturn(
            Optional.of(
                MissionRule.create(
                    missionLog.getCrewParticipant().getCrew(),
                    MissionFrequencyType.DAILY,
                    DailySettlementType.C)));

    processor.confirmOne(MISSION_LOG_ID, NOW);

    assertThat(missionLog.getCertificationStatus()).isEqualTo(CertificationStatus.PENDING_REVIEW);
    verify(autoCertificationDecider, never()).decide(any());
    verify(moderationHistoryRepository, never()).save(any());
  }

  private void givenAutoCertificationContext(MissionLog missionLog) {
    Member systemMember = member(SYSTEM_MEMBER_ID, "system@dondok.internal", "SYSTEM");
    given(missionLogQueryRepository.findByIdWithCrewForAutoCertification(MISSION_LOG_ID))
        .willReturn(Optional.of(missionLog));
    given(settlementRepository.findByCrewId(CREW_ID)).willReturn(Optional.empty());
    given(missionRuleRepository.findByCrewId(CREW_ID))
        .willReturn(
            Optional.of(
                MissionRule.create(
                    missionLog.getCrewParticipant().getCrew(),
                    MissionFrequencyType.DAILY,
                    DailySettlementType.A)));
    given(systemMemberProvider.getSystemMemberId()).willReturn(SYSTEM_MEMBER_ID);
    given(memberRepository.getReferenceById(SYSTEM_MEMBER_ID)).willReturn(systemMember);
  }

  private MissionLog pendingReviewLog() {
    Member host = member(1L, "host@example.com", "host");
    Member participantMember = member(2L, "member@example.com", "member");
    Crew crew =
        Crew.create(
            host,
            "morning crew",
            "daily mission crew",
            null,
            "HEALTH",
            "{}",
            HostPolicyVersion.HOST_POLICY_V1,
            NOW.minusDays(3),
            10_000L,
            2,
            10,
            NOW.minusDays(2),
            NOW.minusDays(1),
            NOW.plusDays(30));
    ReflectionTestUtils.setField(crew, "id", CREW_ID);
    ReflectionTestUtils.setField(crew, "status", CrewStatus.ACTIVE);

    CrewParticipant participant = CrewParticipant.create(crew, participantMember, 10_000L, NOW);
    MissionLog missionLog =
        MissionLog.createPendingReview(
            participant,
            "mission/42/101/image.jpg",
            "daily mission done",
            "9b74c9897bac770ffc029102a200c5de8c0e9e5b9d3c9c7e5f4f5c1a2b3c4d5e",
            NOW.minusHours(1),
            ExifRisk.NORMAL,
            false,
            NOW.toLocalDate().atTime(8, 0));
    ReflectionTestUtils.setField(missionLog, "id", MISSION_LOG_ID);
    return missionLog;
  }

  private Member member(Long id, String email, String nickname) {
    Member member = Member.create(email, "password-hash", nickname);
    ReflectionTestUtils.setField(member, "id", id);
    ReflectionTestUtils.setField(member, "uuid", UUID.randomUUID());
    return member;
  }
}
