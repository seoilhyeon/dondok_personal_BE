package com.oit.dondok.domain.crew.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oit.dondok.domain.crew.entity.CrewNotice;
import com.oit.dondok.domain.crew.entity.CrewNoticeReaction;
import com.oit.dondok.global.util.SeoulDateTimeUtils;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public record NoticeDetailResponse(
    @JsonProperty("notice_id") Long noticeId,
    @JsonProperty("crew_id") Long crewId,
    @JsonProperty("author_member_uuid") UUID authorMemberUuid,
    @JsonProperty("author_nickname") String authorNickname,
    String title,
    String content,
    @JsonProperty("created_at") OffsetDateTime createdAt,
    @JsonProperty("my_reactions") List<String> myReactions,
    @JsonProperty("reaction_counts") Map<String, Long> reactionCounts) {

  public static NoticeDetailResponse from(
      CrewNotice notice, List<CrewNoticeReaction> reactions, long memberId) {
    List<String> myReactions =
        reactions.stream()
            .filter(r -> r.getMember().getId().equals(memberId))
            .map(CrewNoticeReaction::getReactionType)
            .toList();
    Map<String, Long> reactionCounts =
        reactions.stream()
            .collect(
                Collectors.groupingBy(CrewNoticeReaction::getReactionType, Collectors.counting()));
    return new NoticeDetailResponse(
        notice.getId(),
        notice.getCrew().getId(),
        notice.getAuthorMember().getUuid(),
        notice.getAuthorMember().getNickname(),
        notice.getTitle(),
        notice.getContent(),
        SeoulDateTimeUtils.toSeoulOffset(notice.getCreatedAt()),
        myReactions,
        reactionCounts);
  }
}
