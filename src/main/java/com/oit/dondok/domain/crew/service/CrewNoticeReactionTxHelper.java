package com.oit.dondok.domain.crew.service;

import com.oit.dondok.domain.crew.dto.response.ReactionResponse;
import com.oit.dondok.domain.crew.entity.CrewNoticeReaction;
import com.oit.dondok.domain.crew.repository.CrewNoticeReactionRepository;
import com.oit.dondok.domain.member.entity.Member;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class CrewNoticeReactionTxHelper {

  private final CrewNoticeReactionRepository crewNoticeReactionRepository;

  @Transactional
  long removeReaction(Long noticeId, Member member, String normalized) {
    crewNoticeReactionRepository
        .findByCrewNoticeIdAndMemberIdAndReactionType(noticeId, member.getId(), normalized)
        .ifPresent(crewNoticeReactionRepository::delete);
    return member.getId();
  }

  @Transactional(readOnly = true)
  ReactionResponse buildReactionResponse(Long noticeId, long memberId) {
    List<CrewNoticeReaction> all = crewNoticeReactionRepository.findByCrewNoticeId(noticeId);
    List<String> myReactions =
        all.stream()
            .filter(r -> r.getMember().getId().equals(memberId))
            .map(CrewNoticeReaction::getReactionType)
            .toList();
    return new ReactionResponse(noticeId, myReactions, orderByCountThenCreatedAt(all));
  }

  // reaction_counts 정렬: 1) count 내림차순, 2) 최초 등장 시각(createdAt) 오름차순.
  // 같은 횟수면 먼저 등장한 이모지가 왼쪽, 최근에 등장한 이모지가 오른쪽에 온다.
  // 목록/상세/추가응답 집계가 공유한다. createdAt은 영속 엔티티에선 항상 존재하지만
  // 미영속 엔티티(테스트 등) 방어로 nullsLast를 둔다.
  static Map<String, Long> orderByCountThenCreatedAt(List<CrewNoticeReaction> reactions) {
    Map<String, Long> counts = new LinkedHashMap<>();
    Map<String, LocalDateTime> firstSeen = new HashMap<>();
    for (CrewNoticeReaction reaction : reactions) {
      String type = reaction.getReactionType();
      counts.merge(type, 1L, Long::sum);
      firstSeen.compute(type, (key, current) -> earliest(current, reaction.getCreatedAt()));
    }
    return counts.entrySet().stream()
        .sorted(
            Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                .thenComparing(
                    entry -> firstSeen.get(entry.getKey()),
                    Comparator.nullsLast(Comparator.naturalOrder())))
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
  }

  private static LocalDateTime earliest(LocalDateTime a, LocalDateTime b) {
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return a.isBefore(b) ? a : b;
  }
}
