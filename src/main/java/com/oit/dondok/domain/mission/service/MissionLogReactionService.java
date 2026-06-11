package com.oit.dondok.domain.mission.service;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.mission.dto.response.ReactionResponse;
import com.oit.dondok.domain.mission.entity.MissionLogReaction;
import com.oit.dondok.domain.mission.exception.MissionErrorCode;
import com.oit.dondok.domain.mission.repository.FeedQueryRepository;
import com.oit.dondok.domain.mission.repository.MissionLogReactionRepository;
import com.oit.dondok.domain.mission.repository.MissionLogRepository;
import com.oit.dondok.domain.mission.repository.ReactionRow;
import com.oit.dondok.global.exception.CustomException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MissionLogReactionService {

  private final MissionLogRepository missionLogRepository;
  private final CrewParticipantRepository crewParticipantRepository;
  private final MissionLogReactionRepository missionLogReactionRepository;
  private final FeedQueryRepository feedQueryRepository;

  // 멱등 추가: 같은 token 중복/동시 요청도 1개로 수렴(에러 없음).
  @Transactional
  public ReactionResponse addReaction(UUID memberUuid, Long missionLogId, String rawReactionType) {
    String reactionType = normalizeReactionType(rawReactionType);
    Long memberId = authorize(memberUuid, missionLogId);
    missionLogReactionRepository.upsert(missionLogId, memberId, reactionType);
    return aggregate(missionLogId, memberUuid);
  }

  // 멱등 삭제: 매칭 리액션이 없어도 성공. 다른 token은 유지.
  @Transactional
  public ReactionResponse removeReaction(
      UUID memberUuid, Long missionLogId, String rawReactionType) {
    String reactionType = normalizeReactionType(rawReactionType);
    Long memberId = authorize(memberUuid, missionLogId);
    missionLogReactionRepository.deleteReaction(missionLogId, memberId, reactionType);
    return aggregate(missionLogId, memberUuid);
  }

  // 로그 존재 + 호출자의 해당 크루 LOCKED 참여 인가
  // caller member.id 반환(upsert/delete용). 크루 존재 여부는 별도로 노출하지 않는다.
  private Long authorize(UUID memberUuid, Long missionLogId) {
    Long crewId =
        missionLogRepository
            .findCrewIdById(missionLogId)
            .orElseThrow(() -> new CustomException(MissionErrorCode.MISSION_LOG_NOT_FOUND));

    CrewParticipant participant =
        crewParticipantRepository
            .findByCrewIdAndMemberUuid(crewId, memberUuid)
            .filter(p -> p.getStatus() == CrewParticipantStatus.LOCKED)
            .orElseThrow(() -> new CustomException(MissionErrorCode.REACTION_NOT_ALLOWED));

    return participant.getMember().getId(); // LAZY 프록시의 id 접근
  }

  // trim후 blank 거부 + char_length(코드포인트) 1~20 검증만. 정규화/허용목록 검사 없음
  private String normalizeReactionType(String raw) {
    if (raw == null) {
      throw new CustomException(MissionErrorCode.INVALID_REACTION_TYPE);
    }
    String trimmed = raw.trim();
    int codePoints = trimmed.codePointCount(0, trimmed.length());
    if (codePoints < 1 || codePoints > MissionLogReaction.MAX_REACTION_TYPE_LENGTH) {
      throw new CustomException(MissionErrorCode.INVALID_REACTION_TYPE);
    }
    return trimmed;
  }

  // 단건 로그 집계: reaction_counts(token별 count) + my_reactions(caller). 피드 집계 로직과 동일.
  private ReactionResponse aggregate(Long missionLogId, UUID memberUuid) {
    List<ReactionRow> rows = feedQueryRepository.findReactionRows(List.of(missionLogId));
    Map<String, Long> reactionCounts =
        rows.stream().collect(groupingBy(ReactionRow::reactionType, counting()));
    List<String> myReactions =
        rows.stream()
            .filter(r -> r.memberUuid().equals(memberUuid))
            .map(ReactionRow::reactionType)
            .toList();
    return new ReactionResponse(missionLogId, myReactions, reactionCounts);
  }
}
