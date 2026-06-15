package com.oit.dondok.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.dto.response.ReactionResponse;
import com.oit.dondok.domain.mission.exception.MissionErrorCode;
import com.oit.dondok.domain.mission.repository.FeedQueryRepository;
import com.oit.dondok.domain.mission.repository.MissionLogReactionQueryRepository;
import com.oit.dondok.domain.mission.repository.MissionLogReactionRepository;
import com.oit.dondok.domain.mission.repository.ReactionRow;
import com.oit.dondok.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MissionLogReactionServiceTest {

  private static final UUID MEMBER_UUID = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c907");
  private static final UUID OTHER_UUID = UUID.fromString("018f4fd2-aaaa-7a41-9f58-6d07f5c3c907");
  private static final Long MISSION_LOG_ID = 9001L;
  private static final Long CREW_ID = 42L;
  private static final Long MEMBER_ID = 7L;
  private static final LocalDateTime T0 = LocalDateTime.of(2026, 6, 15, 10, 0);

  @Mock private MissionLogReactionQueryRepository missionLogReactionQueryRepository;
  @Mock private CrewParticipantRepository crewParticipantRepository;
  @Mock private MissionLogReactionRepository missionLogReactionRepository;
  @Mock private FeedQueryRepository feedQueryRepository;

  @InjectMocks private MissionLogReactionService service;

  // 추가 성공: upsert가 (logId, memberId, token)으로 호출되고, 집계 응답을 반환한다.
  @Test
  void addReactionUpsertsAndReturnsAggregation() {
    givenLockedParticipant();
    given(feedQueryRepository.findReactionRows(List.of(MISSION_LOG_ID)))
        .willReturn(
            List.of(
                new ReactionRow(MISSION_LOG_ID, "👏", MEMBER_UUID, T0),
                new ReactionRow(MISSION_LOG_ID, "👏", OTHER_UUID, T0.plusSeconds(1)),
                new ReactionRow(MISSION_LOG_ID, "🔥", MEMBER_UUID, T0.plusSeconds(2))));

    ReactionResponse response = service.addReaction(MEMBER_UUID, MISSION_LOG_ID, "👏");

    verify(missionLogReactionRepository).upsert(MISSION_LOG_ID, MEMBER_ID, "👏");
    assertThat(response.missionLogId()).isEqualTo(MISSION_LOG_ID);
    // count 내림차순 정렬 보장: 👏(2) -> 🔥(1) 순서로 노출된다.
    assertThat(response.reactionCounts()).containsExactly(entry("👏", 2L), entry("🔥", 1L));
    assertThat(response.myReactions()).containsExactlyInAnyOrder("👏", "🔥");
  }

  // 동률 정렬: 토큰순이 아니라 최초 등장 시각(createdAt) 오름차순. 🔥가 먼저 등장 → 🔥, 👏 순.
  @Test
  void reactionCountsTieBreakByCreatedAt() {
    givenLockedParticipant();
    given(feedQueryRepository.findReactionRows(List.of(MISSION_LOG_ID)))
        .willReturn(
            List.of(
                new ReactionRow(MISSION_LOG_ID, "🔥", OTHER_UUID, T0),
                new ReactionRow(MISSION_LOG_ID, "👏", OTHER_UUID, T0.plusSeconds(5))));

    ReactionResponse response = service.addReaction(MEMBER_UUID, MISSION_LOG_ID, "👏");

    assertThat(response.reactionCounts()).containsExactly(entry("🔥", 1L), entry("👏", 1L));
  }

  // reaction_type은 trim 후 저장된다 (앞뒤 공백 제거).
  @Test
  void addReactionTrimsReactionType() {
    givenLockedParticipant();
    given(feedQueryRepository.findReactionRows(List.of(MISSION_LOG_ID))).willReturn(List.of());

    service.addReaction(MEMBER_UUID, MISSION_LOG_ID, "  👏  ");

    verify(missionLogReactionRepository).upsert(MISSION_LOG_ID, MEMBER_ID, "👏");
  }

  // 삭제 성공: deleteReaction이 호출되고 집계 응답을 반환한다.
  @Test
  void removeReactionDeletesAndReturnsAggregation() {
    givenLockedParticipant();
    given(feedQueryRepository.findReactionRows(List.of(MISSION_LOG_ID)))
        .willReturn(List.of(new ReactionRow(MISSION_LOG_ID, "🔥", OTHER_UUID, T0)));

    ReactionResponse response = service.removeReaction(MEMBER_UUID, MISSION_LOG_ID, "👏");

    verify(missionLogReactionQueryRepository).deleteReaction(MISSION_LOG_ID, MEMBER_ID, "👏");
    assertThat(response.reactionCounts()).containsOnly(entry("🔥", 1L));
    assertThat(response.myReactions()).isEmpty();
  }

  // 로그가 없으면 MISSION_LOG_NOT_FOUND, upsert는 호출되지 않는다.
  @Test
  void addReactionThrowsWhenMissionLogNotFound() {
    given(missionLogReactionQueryRepository.findCrewIdByMissionLogId(MISSION_LOG_ID))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> service.addReaction(MEMBER_UUID, MISSION_LOG_ID, "👏"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.MISSION_LOG_NOT_FOUND);

    verify(missionLogReactionRepository, never()).upsert(any(), any(), any());
  }

  // 해당 크루 참여자가 아니면 REACTION_NOT_ALLOWED.
  @Test
  void addReactionThrowsWhenNotParticipant() {
    given(missionLogReactionQueryRepository.findCrewIdByMissionLogId(MISSION_LOG_ID))
        .willReturn(Optional.of(CREW_ID));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, MEMBER_UUID))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> service.addReaction(MEMBER_UUID, MISSION_LOG_ID, "👏"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.REACTION_NOT_ALLOWED);

    verify(missionLogReactionRepository, never()).upsert(any(), any(), any());
  }

  // 참여자이지만 LOCKED 상태가 아니면 REACTION_NOT_ALLOWED.
  @Test
  void addReactionThrowsWhenParticipantNotLocked() {
    given(missionLogReactionQueryRepository.findCrewIdByMissionLogId(MISSION_LOG_ID))
        .willReturn(Optional.of(CREW_ID));
    CrewParticipant participant = mock(CrewParticipant.class);
    given(participant.getStatus()).willReturn(CrewParticipantStatus.PENDING);
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, MEMBER_UUID))
        .willReturn(Optional.of(participant));

    assertThatThrownBy(() -> service.addReaction(MEMBER_UUID, MISSION_LOG_ID, "👏"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.REACTION_NOT_ALLOWED);

    verify(missionLogReactionRepository, never()).upsert(any(), any(), any());
  }

  // 토큰 검증은 DB 접근 전에 수행된다: null/blank/길이초과는 INVALID_REACTION_TYPE이며 어떤 repo도 호출하지 않는다.
  @Test
  void addReactionThrowsWhenReactionTypeNull() {
    assertThatThrownBy(() -> service.addReaction(MEMBER_UUID, MISSION_LOG_ID, null))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.INVALID_REACTION_TYPE);

    verifyNoInteractions(
        missionLogReactionQueryRepository,
        crewParticipantRepository,
        missionLogReactionRepository,
        feedQueryRepository);
  }

  @Test
  void addReactionThrowsWhenReactionTypeBlank() {
    assertThatThrownBy(() -> service.addReaction(MEMBER_UUID, MISSION_LOG_ID, "   "))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.INVALID_REACTION_TYPE);

    verifyNoInteractions(
        missionLogReactionQueryRepository,
        crewParticipantRepository,
        missionLogReactionRepository,
        feedQueryRepository);
  }

  // char_length(코드포인트) 20 초과는 INVALID_REACTION_TYPE.
  @Test
  void addReactionThrowsWhenReactionTypeTooLong() {
    String tooLong = "a".repeat(21);

    assertThatThrownBy(() -> service.addReaction(MEMBER_UUID, MISSION_LOG_ID, tooLong))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.INVALID_REACTION_TYPE);

    verifyNoInteractions(
        missionLogReactionQueryRepository,
        crewParticipantRepository,
        missionLogReactionRepository,
        feedQueryRepository);
  }

  // 삭제도 동일 인가가 적용된다: 비참여자면 REACTION_NOT_ALLOWED, delete 미호출.
  @Test
  void removeReactionThrowsWhenNotParticipant() {
    given(missionLogReactionQueryRepository.findCrewIdByMissionLogId(MISSION_LOG_ID))
        .willReturn(Optional.of(CREW_ID));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, MEMBER_UUID))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> service.removeReaction(MEMBER_UUID, MISSION_LOG_ID, "👏"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.REACTION_NOT_ALLOWED);

    verify(missionLogReactionQueryRepository, never()).deleteReaction(any(), any(), any());
  }

  // 삭제: 로그가 없으면 MISSION_LOG_NOT_FOUND, deleteReaction 미호출.
  @Test
  void removeReactionThrowsWhenMissionLogNotFound() {
    given(missionLogReactionQueryRepository.findCrewIdByMissionLogId(MISSION_LOG_ID))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> service.removeReaction(MEMBER_UUID, MISSION_LOG_ID, "👏"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.MISSION_LOG_NOT_FOUND);

    verify(missionLogReactionQueryRepository, never()).deleteReaction(any(), any(), any());
  }

  // 삭제: 참여자이지만 LOCKED 상태가 아니면 REACTION_NOT_ALLOWED.
  @Test
  void removeReactionThrowsWhenParticipantNotLocked() {
    given(missionLogReactionQueryRepository.findCrewIdByMissionLogId(MISSION_LOG_ID))
        .willReturn(Optional.of(CREW_ID));
    CrewParticipant participant = mock(CrewParticipant.class);
    given(participant.getStatus()).willReturn(CrewParticipantStatus.PENDING);
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, MEMBER_UUID))
        .willReturn(Optional.of(participant));

    assertThatThrownBy(() -> service.removeReaction(MEMBER_UUID, MISSION_LOG_ID, "👏"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.REACTION_NOT_ALLOWED);

    verify(missionLogReactionQueryRepository, never()).deleteReaction(any(), any(), any());
  }

  // 삭제: 토큰 검증은 DB 접근 전에 수행된다 — null/blank/길이초과는 INVALID_REACTION_TYPE이며 repo 미호출.
  @Test
  void removeReactionThrowsWhenReactionTypeNull() {
    assertThatThrownBy(() -> service.removeReaction(MEMBER_UUID, MISSION_LOG_ID, null))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.INVALID_REACTION_TYPE);

    verifyNoInteractions(
        missionLogReactionQueryRepository,
        crewParticipantRepository,
        missionLogReactionRepository,
        feedQueryRepository);
  }

  @Test
  void removeReactionThrowsWhenReactionTypeBlank() {
    assertThatThrownBy(() -> service.removeReaction(MEMBER_UUID, MISSION_LOG_ID, "   "))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.INVALID_REACTION_TYPE);

    verifyNoInteractions(
        missionLogReactionQueryRepository,
        crewParticipantRepository,
        missionLogReactionRepository,
        feedQueryRepository);
  }

  @Test
  void removeReactionThrowsWhenReactionTypeTooLong() {
    String tooLong = "a".repeat(21);

    assertThatThrownBy(() -> service.removeReaction(MEMBER_UUID, MISSION_LOG_ID, tooLong))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.INVALID_REACTION_TYPE);

    verifyNoInteractions(
        missionLogReactionQueryRepository,
        crewParticipantRepository,
        missionLogReactionRepository,
        feedQueryRepository);
  }

  // LOCKED 참여자(MEMBER_UUID/MEMBER_ID) 스텁: 로그 존재 + 인가 통과 경로.
  private void givenLockedParticipant() {
    given(missionLogReactionQueryRepository.findCrewIdByMissionLogId(MISSION_LOG_ID))
        .willReturn(Optional.of(CREW_ID));
    CrewParticipant participant = mock(CrewParticipant.class);
    Member member = mock(Member.class);
    given(participant.getStatus()).willReturn(CrewParticipantStatus.LOCKED);
    given(participant.getMember()).willReturn(member);
    given(member.getId()).willReturn(MEMBER_ID);
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, MEMBER_UUID))
        .willReturn(Optional.of(participant));
  }
}
