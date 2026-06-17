package com.oit.dondok.domain.crew.service;

import com.oit.dondok.domain.crew.dto.request.AddReactionRequest;
import com.oit.dondok.domain.crew.dto.request.CreateNoticeRequest;
import com.oit.dondok.domain.crew.dto.request.UpdateNoticeRequest;
import com.oit.dondok.domain.crew.dto.response.NoticeDetailResponse;
import com.oit.dondok.domain.crew.dto.response.NoticeItemResponse;
import com.oit.dondok.domain.crew.dto.response.NoticeListResponse;
import com.oit.dondok.domain.crew.dto.response.ReactionResponse;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewNotice;
import com.oit.dondok.domain.crew.entity.CrewNoticeReaction;
import com.oit.dondok.domain.crew.entity.CrewNoticeStatus;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.repository.CrewNoticeReactionRepository;
import com.oit.dondok.domain.crew.repository.CrewNoticeRepository;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.domain.notification.port.NotificationPayload;
import com.oit.dondok.domain.notification.port.NotificationSender;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.util.ReactionCountOrdering;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrewNoticeService {

  private static final int MAX_LIMIT = 100;

  private final CrewRepository crewRepository;
  private final CrewParticipantRepository crewParticipantRepository;
  private final MemberRepository memberRepository;
  private final CrewNoticeRepository crewNoticeRepository;
  private final CrewNoticeReactionRepository crewNoticeReactionRepository;
  private final CrewNoticeReactionTxHelper crewNoticeReactionTxHelper;
  private final NotificationSender notificationSender;

  @Transactional(readOnly = true)
  public NoticeListResponse findNoticeList(Long crewId, String cursor, int limit, UUID memberUuid) {
    Member member = requireLockedMember(crewId, memberUuid);
    int effectiveLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
    Long cursorId = decodeCursor(cursor);

    List<CrewNotice> rows =
        crewNoticeRepository.findByCrewIdAndStatusAndIdLessThanOrderByIdDesc(
            crewId,
            CrewNoticeStatus.VISIBLE,
            cursorId == null ? Long.MAX_VALUE : cursorId,
            PageRequest.of(0, effectiveLimit + 1));

    boolean hasNext = rows.size() > effectiveLimit;
    List<CrewNotice> pageRows = hasNext ? rows.subList(0, effectiveLimit) : rows;
    String nextCursor = hasNext ? encodeCursor(pageRows.get(pageRows.size() - 1).getId()) : null;

    List<Long> noticeIds = pageRows.stream().map(CrewNotice::getId).toList();
    Map<Long, List<CrewNoticeReaction>> reactionsByNotice =
        noticeIds.isEmpty()
            ? Map.of()
            : crewNoticeReactionRepository.findByCrewNoticeIdIn(noticeIds).stream()
                .collect(Collectors.groupingBy(r -> r.getCrewNotice().getId()));

    List<NoticeItemResponse> items =
        pageRows.stream()
            .map(
                notice -> {
                  List<CrewNoticeReaction> reactions =
                      reactionsByNotice.getOrDefault(notice.getId(), List.of());
                  List<String> myReactions =
                      reactions.stream()
                          .filter(r -> r.getMember().getId().equals(member.getId()))
                          .map(CrewNoticeReaction::getReactionType)
                          .toList();
                  Map<String, Long> reactionCounts =
                      ReactionCountOrdering.orderByCountThenCreatedAt(
                          reactions,
                          CrewNoticeReaction::getReactionType,
                          CrewNoticeReaction::getCreatedAt);
                  return NoticeItemResponse.from(notice, myReactions, reactionCounts);
                })
            .toList();

    return new NoticeListResponse(items, nextCursor);
  }

  @Transactional(readOnly = true)
  public NoticeDetailResponse findNoticeDetail(Long crewId, Long noticeId, UUID memberUuid) {
    Member member = requireLockedMember(crewId, memberUuid);
    CrewNotice notice = requireVisibleNotice(noticeId, crewId);
    List<CrewNoticeReaction> reactions =
        crewNoticeReactionRepository.findByCrewNoticeIdIn(List.of(noticeId));
    List<String> myReactions =
        reactions.stream()
            .filter(r -> r.getMember().getId().equals(member.getId()))
            .map(CrewNoticeReaction::getReactionType)
            .toList();
    return NoticeDetailResponse.from(
        notice,
        myReactions,
        ReactionCountOrdering.orderByCountThenCreatedAt(
            reactions, CrewNoticeReaction::getReactionType, CrewNoticeReaction::getCreatedAt));
  }

  @Transactional
  public void createNotice(Long crewId, UUID memberUuid, CreateNoticeRequest request) {
    Crew crew = requireHostCrew(crewId, memberUuid);
    Member author =
        memberRepository
            .findByUuid(memberUuid)
            .orElseThrow(() -> new CustomException(CrewErrorCode.MEMBER_NOT_FOUND));
    CrewNotice notice =
        crewNoticeRepository.save(
            CrewNotice.create(crew, author, request.title(), request.content()));
    List<CrewParticipant> lockedParticipants =
        crewParticipantRepository.findByCrewIdAndStatusIn(
            crewId, List.of(CrewParticipantStatus.LOCKED));
    for (CrewParticipant p : lockedParticipants) {
      notificationSender.send(
          p.getMember(),
          new NotificationPayload(
              "CREW_NOTICE_POSTED",
              "crew_notice",
              String.valueOf(notice.getId()),
              "dondok://crews/" + crewId + "/notices/" + notice.getId(),
              "'" + crew.getTitle() + "' 크루에 새 공지가 등록되었습니다."));
    }
  }

  @Transactional
  public void updateNotice(
      Long crewId, Long noticeId, UUID memberUuid, UpdateNoticeRequest request) {
    requireHostCrew(crewId, memberUuid);
    requireVisibleNotice(noticeId, crewId).update(request.title(), request.content());
  }

  @Transactional
  public void deleteNotice(Long crewId, Long noticeId, UUID memberUuid) {
    requireHostCrew(crewId, memberUuid);
    requireVisibleNotice(noticeId, crewId).softDelete();
  }

  @Transactional
  public ReactionResponse addReaction(
      Long crewId, Long noticeId, UUID memberUuid, AddReactionRequest request) {
    String reactionType = normalizeReactionType(request.reactionType());
    CrewNotice notice = requireVisibleNotice(noticeId, crewId);
    Member member = requireReactionPermission(crewId, memberUuid);
    crewNoticeReactionRepository.upsert(notice.getId(), member.getId(), reactionType);
    Member noticeAuthor = notice.getAuthorMember();
    if (!noticeAuthor.getId().equals(member.getId())) {
      try {
        notificationSender.send(
            noticeAuthor,
            new NotificationPayload(
                "CREW_NOTICE_REACTION_ADDED",
                "crew_notice",
                String.valueOf(noticeId),
                "dondok://crews/" + crewId + "/notices/" + noticeId,
                member.getNickname() + "님이 공지에 " + reactionType + " 리액션을 달았습니다"));
      } catch (RuntimeException e) {
        log.warn("[알림] 공지 리액션 알림 발송 실패 noticeId={}", noticeId, e);
      }
    }
    return crewNoticeReactionTxHelper.buildReactionResponse(noticeId, member.getId());
  }

  @Transactional
  public ReactionResponse removeReaction(
      Long crewId, Long noticeId, UUID memberUuid, String reactionType) {
    String normalized = normalizeReactionType(reactionType);
    requireVisibleNotice(noticeId, crewId);
    Member member = requireReactionPermission(crewId, memberUuid);
    long memberId = crewNoticeReactionTxHelper.removeReaction(noticeId, member, normalized);
    return crewNoticeReactionTxHelper.buildReactionResponse(noticeId, memberId);
  }

  private String normalizeReactionType(String raw) {
    if (raw == null) {
      throw new CustomException(CrewErrorCode.INVALID_REACTION_TYPE);
    }
    String trimmed = raw.strip();
    int codePoints = trimmed.codePointCount(0, trimmed.length());
    if (trimmed.isBlank() || codePoints > CrewNoticeReaction.MAX_REACTION_TYPE_LENGTH) {
      throw new CustomException(CrewErrorCode.INVALID_REACTION_TYPE);
    }
    return trimmed;
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

  private Member requireReactionPermission(Long crewId, UUID memberUuid) {
    return crewParticipantRepository
        .findByCrewIdAndMemberUuid(crewId, memberUuid)
        .filter(p -> p.getStatus() == CrewParticipantStatus.LOCKED)
        .map(CrewParticipant::getMember)
        .orElseThrow(() -> new CustomException(CrewErrorCode.REACTION_NOT_ALLOWED));
  }

  private Crew requireHostCrew(Long crewId, UUID memberUuid) {
    Crew crew =
        crewRepository
            .findById(crewId)
            .orElseThrow(() -> new CustomException(CrewErrorCode.CREW_NOT_FOUND));
    if (!crew.getHostMember().getUuid().equals(memberUuid)) {
      throw new CustomException(CrewErrorCode.FORBIDDEN_NOT_HOST);
    }
    return crew;
  }

  private static Long decodeCursor(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(
          new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new CustomException(CrewErrorCode.INVALID_CURSOR);
    }
  }

  private static String encodeCursor(Long id) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(String.valueOf(id).getBytes(StandardCharsets.UTF_8));
  }
}
