package com.oit.dondok.domain.crew.service;

import com.oit.dondok.domain.crew.dto.request.AddReactionRequest;
import com.oit.dondok.domain.crew.dto.request.CreateNoticeRequest;
import com.oit.dondok.domain.crew.dto.request.UpdateNoticeRequest;
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
import com.oit.dondok.global.exception.CustomException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrewNoticeService {

  private static final int MAX_LIMIT = 100;

  private final CrewRepository crewRepository;
  private final CrewParticipantRepository crewParticipantRepository;
  private final MemberRepository memberRepository;
  private final CrewNoticeRepository crewNoticeRepository;
  private final CrewNoticeReactionRepository crewNoticeReactionRepository;

  @Transactional(readOnly = true)
  public NoticeListResponse findNoticeList(Long crewId, String cursor, int limit, UUID memberUuid) {
    validateCrewAccess(crewId, memberUuid);
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

    return new NoticeListResponse(
        pageRows.stream().map(NoticeItemResponse::from).toList(), nextCursor);
  }

  @Transactional
  public void createNotice(Long crewId, UUID memberUuid, CreateNoticeRequest request) {
    validateHostCrew(crewId, memberUuid);
    Crew crew =
        crewRepository
            .findById(crewId)
            .orElseThrow(() -> new CustomException(CrewErrorCode.CREW_NOT_FOUND));
    Member author =
        memberRepository
            .findByUuid(memberUuid)
            .orElseThrow(() -> new CustomException(CrewErrorCode.MEMBER_NOT_FOUND));
    crewNoticeRepository.save(CrewNotice.create(crew, author, request.title(), request.content()));
  }

  @Transactional
  public void updateNotice(
      Long crewId, Long noticeId, UUID memberUuid, UpdateNoticeRequest request) {
    validateHostCrew(crewId, memberUuid);
    requireVisibleNotice(noticeId, crewId).update(request.title(), request.content());
  }

  @Transactional
  public void deleteNotice(Long crewId, Long noticeId, UUID memberUuid) {
    validateHostCrew(crewId, memberUuid);
    requireVisibleNotice(noticeId, crewId).softDelete();
  }

  @Transactional
  public ReactionResponse addReaction(
      Long crewId, Long noticeId, UUID memberUuid, AddReactionRequest request) {
    Member member = requireLockedMember(crewId, memberUuid);
    CrewNotice notice = requireVisibleNotice(noticeId, crewId);
    if (!crewNoticeReactionRepository.existsByCrewNoticeIdAndMemberIdAndReactionType(
        noticeId, member.getId(), request.reactionType())) {
      crewNoticeReactionRepository.save(
          CrewNoticeReaction.create(notice, member, request.reactionType()));
    }
    return buildReactionResponse(noticeId, member.getId());
  }

  @Transactional
  public ReactionResponse removeReaction(
      Long crewId, Long noticeId, UUID memberUuid, String reactionType) {
    Member member = requireLockedMember(crewId, memberUuid);
    requireVisibleNotice(noticeId, crewId);
    crewNoticeReactionRepository
        .findByCrewNoticeIdAndMemberIdAndReactionType(noticeId, member.getId(), reactionType)
        .ifPresent(crewNoticeReactionRepository::delete);
    return buildReactionResponse(noticeId, member.getId());
  }

  private ReactionResponse buildReactionResponse(Long noticeId, Long memberId) {
    List<CrewNoticeReaction> all = crewNoticeReactionRepository.findByCrewNoticeId(noticeId);
    List<String> myReactions =
        all.stream()
            .filter(r -> r.getMember().getId().equals(memberId))
            .map(CrewNoticeReaction::getReactionType)
            .toList();
    Map<String, Long> reactionCounts =
        all.stream()
            .collect(
                Collectors.groupingBy(CrewNoticeReaction::getReactionType, Collectors.counting()));
    return new ReactionResponse(noticeId, myReactions, reactionCounts);
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

  private void validateCrewAccess(Long crewId, UUID memberUuid) {
    requireLockedMember(crewId, memberUuid);
  }

  private void validateHostCrew(Long crewId, UUID memberUuid) {
    if (crewRepository.existsByIdAndHostMemberUuid(crewId, memberUuid)) {
      return;
    }
    if (!crewRepository.existsById(crewId)) {
      throw new CustomException(CrewErrorCode.CREW_NOT_FOUND);
    }
    throw new CustomException(CrewErrorCode.FORBIDDEN_NOT_HOST);
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
