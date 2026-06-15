package com.oit.dondok.domain.crew.service;

import com.oit.dondok.domain.crew.dto.response.ReactionResponse;
import com.oit.dondok.domain.crew.entity.CrewNoticeReaction;
import com.oit.dondok.domain.crew.repository.CrewNoticeReactionRepository;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.global.util.ReactionCountOrdering;
import java.util.List;
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
    return new ReactionResponse(
        noticeId,
        myReactions,
        ReactionCountOrdering.orderByCountThenCreatedAt(
            all, CrewNoticeReaction::getReactionType, CrewNoticeReaction::getCreatedAt));
  }
}
