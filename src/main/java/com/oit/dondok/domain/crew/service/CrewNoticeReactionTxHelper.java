package com.oit.dondok.domain.crew.service;

import com.oit.dondok.domain.crew.dto.response.ReactionResponse;
import com.oit.dondok.domain.crew.entity.CrewNoticeReaction;
import com.oit.dondok.domain.crew.repository.CrewNoticeReactionRepository;
import com.oit.dondok.domain.member.entity.Member;
import java.util.Comparator;
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
    Map<String, Long> reactionCounts =
        sortedByCountDesc(
            all.stream()
                .collect(
                    Collectors.groupingBy(
                        CrewNoticeReaction::getReactionType, Collectors.counting())));
    return new ReactionResponse(noticeId, myReactions, reactionCounts);
  }

  private static Map<String, Long> sortedByCountDesc(Map<String, Long> counts) {
    return counts.entrySet().stream()
        .sorted(
            Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey()))
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
  }
}
