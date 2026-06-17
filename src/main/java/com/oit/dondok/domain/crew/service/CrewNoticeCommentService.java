package com.oit.dondok.domain.crew.service;

import com.oit.dondok.domain.crew.dto.request.CreateCommentRequest;
import com.oit.dondok.domain.crew.dto.request.UpdateCommentRequest;
import com.oit.dondok.domain.crew.dto.response.CommentItemResponse;
import com.oit.dondok.domain.crew.dto.response.CommentListResponse;
import com.oit.dondok.domain.crew.entity.CrewNotice;
import com.oit.dondok.domain.crew.entity.CrewNoticeComment;
import com.oit.dondok.domain.crew.entity.CrewNoticeCommentStatus;
import com.oit.dondok.domain.crew.entity.CrewNoticeStatus;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.repository.CrewNoticeCommentRepository;
import com.oit.dondok.domain.crew.repository.CrewNoticeRepository;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import com.oit.dondok.domain.image.port.ImageDeliveryPort;
import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.notification.port.NotificationPayload;
import com.oit.dondok.domain.notification.port.NotificationSender;
import com.oit.dondok.global.exception.CustomException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrewNoticeCommentService {

  private static final int MAX_LIMIT = 100;
  private static final String CURSOR_SEPARATOR = "_";
  private static final Duration PROFILE_IMAGE_URL_TTL = Duration.ofMinutes(10);

  private final CrewRepository crewRepository;
  private final CrewParticipantRepository crewParticipantRepository;
  private final CrewNoticeRepository crewNoticeRepository;
  private final CrewNoticeCommentRepository crewNoticeCommentRepository;
  private final ImageDeliveryPort imageDeliveryPort;
  private final NotificationSender notificationSender;

  @Transactional(readOnly = true)
  public CommentListResponse findCommentList(
      Long crewId, Long noticeId, String cursor, int limit, UUID memberUuid) {
    requireLockedMember(crewId, memberUuid);
    requireVisibleNotice(noticeId, crewId);

    int effectiveLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
    PageRequest pageable = PageRequest.of(0, effectiveLimit + 1);

    List<CrewNoticeComment> rows;
    if (cursor == null || cursor.isBlank()) {
      rows =
          crewNoticeCommentRepository.findByCrewNoticeIdAndStatusOrderByCreatedAtAscIdAsc(
              noticeId, CrewNoticeCommentStatus.VISIBLE, pageable);
    } else {
      CursorValues cv = decodeCursor(cursor);
      rows =
          crewNoticeCommentRepository.findAfterCursor(
              noticeId, CrewNoticeCommentStatus.VISIBLE, cv.createdAt(), cv.id(), pageable);
    }

    boolean hasNext = rows.size() > effectiveLimit;
    List<CrewNoticeComment> pageRows = hasNext ? rows.subList(0, effectiveLimit) : rows;
    String nextCursor = hasNext ? encodeCursor(pageRows.get(pageRows.size() - 1)) : null;

    List<CommentItemResponse> items =
        pageRows.stream()
            .map(
                c ->
                    CommentItemResponse.from(
                        c, resolveProfileImageUrl(c.getMember().getProfileImageS3Key())))
            .toList();
    return new CommentListResponse(items, nextCursor);
  }

  @Transactional
  public void createComment(
      Long crewId, Long noticeId, UUID memberUuid, CreateCommentRequest request) {
    Member member = requireLockedMember(crewId, memberUuid);
    CrewNotice notice = requireVisibleNotice(noticeId, crewId);
    crewNoticeCommentRepository.save(CrewNoticeComment.create(notice, member, request.content()));
    Member noticeAuthor = notice.getAuthorMember();
    if (!noticeAuthor.getId().equals(member.getId())) {
      try {
        notificationSender.send(
            noticeAuthor,
            new NotificationPayload(
                "CREW_NOTICE_COMMENT_ADDED",
                "crew_notice",
                String.valueOf(noticeId),
                "dondok://crews/" + crewId + "/notices/" + noticeId,
                member.getNickname() + "님이 공지에 댓글을 남겼습니다 →"));
      } catch (RuntimeException e) {
        log.warn("[알림] 공지 댓글 알림 발송 실패 noticeId={}", noticeId, e);
      }
    }
  }

  @Transactional
  public void updateComment(
      Long crewId, Long noticeId, Long commentId, UUID memberUuid, UpdateCommentRequest request) {
    requireVisibleNotice(noticeId, crewId);
    CrewNoticeComment comment = requireActiveComment(commentId, noticeId);
    requireCommentAuthor(comment, memberUuid);
    comment.updateContent(request.content());
  }

  @Transactional
  public void deleteComment(Long crewId, Long noticeId, Long commentId, UUID memberUuid) {
    requireVisibleNotice(noticeId, crewId);
    CrewNoticeComment comment = requireActiveComment(commentId, noticeId);
    requireCommentAuthor(comment, memberUuid);
    comment.softDelete();
  }

  private Member requireLockedMember(Long crewId, UUID memberUuid) {
    if (!crewRepository.existsById(crewId)) {
      throw new CustomException(CrewErrorCode.CREW_NOT_FOUND);
    }
    return crewParticipantRepository
        .findByCrewIdAndMemberUuid(crewId, memberUuid)
        .filter(p -> p.getStatus() == CrewParticipantStatus.LOCKED)
        .map(CrewParticipant::getMember)
        .orElseThrow(() -> new CustomException(CrewErrorCode.CREW_ACCESS_DENIED));
  }

  private CrewNotice requireVisibleNotice(Long noticeId, Long crewId) {
    CrewNotice notice =
        crewNoticeRepository
            .findById(noticeId)
            .orElseThrow(() -> new CustomException(CrewErrorCode.NOTICE_NOT_FOUND));
    if (!notice.getCrew().getId().equals(crewId)
        || notice.getStatus() != CrewNoticeStatus.VISIBLE) {
      throw new CustomException(CrewErrorCode.NOTICE_NOT_FOUND);
    }
    return notice;
  }

  private CrewNoticeComment requireActiveComment(Long commentId, Long noticeId) {
    CrewNoticeComment comment =
        crewNoticeCommentRepository
            .findById(commentId)
            .orElseThrow(() -> new CustomException(CrewErrorCode.COMMENT_NOT_FOUND));
    if (!comment.getCrewNotice().getId().equals(noticeId)
        || comment.getStatus() == CrewNoticeCommentStatus.DELETED) {
      throw new CustomException(CrewErrorCode.COMMENT_NOT_FOUND);
    }
    return comment;
  }

  private void requireCommentAuthor(CrewNoticeComment comment, UUID memberUuid) {
    if (!comment.getMember().getUuid().equals(memberUuid)) {
      throw new CustomException(CrewErrorCode.COMMENT_FORBIDDEN);
    }
  }

  private static String encodeCursor(CrewNoticeComment comment) {
    LocalDateTime createdAt = comment.getCreatedAt();
    // format: epochSecond_id  (both URL-safe)
    return createdAt.toEpochSecond(java.time.ZoneOffset.UTC) + CURSOR_SEPARATOR + comment.getId();
  }

  private static CursorValues decodeCursor(String cursor) {
    try {
      int sep = cursor.lastIndexOf(CURSOR_SEPARATOR);
      long epochSecond = Long.parseLong(cursor.substring(0, sep));
      long id = Long.parseLong(cursor.substring(sep + 1));
      LocalDateTime createdAt =
          LocalDateTime.ofEpochSecond(epochSecond, 0, java.time.ZoneOffset.UTC);
      return new CursorValues(createdAt, id);
    } catch (Exception e) {
      throw new CustomException(CrewErrorCode.INVALID_CURSOR);
    }
  }

  private String resolveProfileImageUrl(String s3Key) {
    if (s3Key == null) {
      return null;
    }
    return imageDeliveryPort
        .createDeliveryUrl(new ImageObjectKey(s3Key), PROFILE_IMAGE_URL_TTL)
        .url();
  }

  private record CursorValues(LocalDateTime createdAt, Long id) {}
}
