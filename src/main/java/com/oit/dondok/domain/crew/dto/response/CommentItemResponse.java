package com.oit.dondok.domain.crew.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oit.dondok.domain.crew.entity.CrewNoticeComment;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

public record CommentItemResponse(
    @JsonProperty("comment_id") Long commentId,
    @JsonProperty("notice_id") Long noticeId,
    @JsonProperty("author_member_uuid") UUID authorMemberUuid,
    String nickname,
    @JsonProperty("author_profile_image_url") String profileImageUrl,
    String content,
    @JsonProperty("created_at") OffsetDateTime createdAt) {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

  public static CommentItemResponse from(CrewNoticeComment comment, String profileImageUrl) {
    return new CommentItemResponse(
        comment.getId(),
        comment.getCrewNotice().getId(),
        comment.getMember().getUuid(),
        comment.getMember().getNickname(),
        profileImageUrl,
        comment.getContent(),
        toSeoulOffset(comment.getCreatedAt()));
  }

  private static OffsetDateTime toSeoulOffset(LocalDateTime ldt) {
    return ldt == null ? null : ldt.atZone(SEOUL_ZONE).toOffsetDateTime();
  }
}
