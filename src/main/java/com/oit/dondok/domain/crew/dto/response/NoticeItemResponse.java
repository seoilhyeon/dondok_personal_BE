package com.oit.dondok.domain.crew.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oit.dondok.domain.crew.entity.CrewNotice;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record NoticeItemResponse(
    @JsonProperty("notice_id") Long noticeId,
    @JsonProperty("crew_id") Long crewId,
    @JsonProperty("author_member_uuid") UUID authorMemberUuid,
    @JsonProperty("author_nickname") String authorNickname,
    String title,
    String content,
    @JsonProperty("created_at") OffsetDateTime createdAt,
    @JsonProperty("my_reactions") List<String> myReactions,
    @JsonProperty("reaction_counts") Map<String, Long> reactionCounts) {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

  public static NoticeItemResponse from(
      CrewNotice notice, List<String> myReactions, Map<String, Long> reactionCounts) {
    return new NoticeItemResponse(
        notice.getId(),
        notice.getCrew().getId(),
        notice.getAuthorMember().getUuid(),
        notice.getAuthorMember().getNickname(),
        notice.getTitle(),
        notice.getContent(),
        toSeoulOffset(notice.getCreatedAt()),
        myReactions,
        reactionCounts);
  }

  private static OffsetDateTime toSeoulOffset(LocalDateTime ldt) {
    return ldt == null ? null : ldt.atZone(SEOUL_ZONE).toOffsetDateTime();
  }
}
