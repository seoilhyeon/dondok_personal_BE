package com.oit.dondok.domain.mission.service;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.image.port.ImageDeliveryPort;
import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.mission.dto.response.AvailableCrewResponse;
import com.oit.dondok.domain.mission.dto.response.FeedItemResponse;
import com.oit.dondok.domain.mission.dto.response.FeedResponse;
import com.oit.dondok.domain.mission.exception.MissionErrorCode;
import com.oit.dondok.domain.mission.repository.FeedItemRow;
import com.oit.dondok.domain.mission.repository.FeedQueryRepository;
import com.oit.dondok.domain.mission.repository.ReactionRow;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.util.SeoulDateTimeUtils;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedService {
  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 100;
  private static final Duration IMAGE_URL_TTL = Duration.ofMinutes(10);

  private final FeedQueryRepository feedQueryRepository;
  private final ImageDeliveryPort imageDeliveryPort;

  public FeedResponse getFeed(
      UUID memberUuid, Long crewId, LocalDate from, LocalDate to, String cursor, Integer limit) {
    List<AvailableCrewResponse> myCrews = feedQueryRepository.findParticipatingCrews(memberUuid);
    Set<Long> myCrewIds = myCrews.stream().map(AvailableCrewResponse::crewId).collect(toSet());

    Collection<Long> scopeCrewIds = resolveScope(crewId, myCrewIds);
    LocalDateTime fromInclusive = (from == null) ? null : from.atStartOfDay();
    LocalDate effectiveTo = (to != null) ? to : from; // 단일 날짜는 from만 와도 그 날짜로
    LocalDateTime toExclusive =
        (effectiveTo == null) ? null : effectiveTo.plusDays(1).atStartOfDay();
    Cursor cur = parseCursor(cursor);
    int pageSize = clampLimit(limit);

    List<FeedItemRow> rows =
        feedQueryRepository.findFeedItems(
            scopeCrewIds, fromInclusive, toExclusive, cur.serverTime(), cur.id(), pageSize);

    boolean hasNext = rows.size() > pageSize;
    List<FeedItemRow> page = hasNext ? rows.subList(0, pageSize) : rows;
    if (page.isEmpty()) {
      return new FeedResponse(myCrews, List.of(), null);
    }
    String nextCursor = hasNext ? encodeCursor(page.get(page.size() - 1)) : null;

    List<Long> logIds = page.stream().map(FeedItemRow::missionLogId).toList();
    List<ReactionRow> reactions = feedQueryRepository.findReactionRows(logIds);
    Map<Long, Map<String, Long>> reactionCounts = buildReactionCounts(reactions);
    Map<Long, List<String>> myReactions = buildMyReactions(reactions, memberUuid);

    List<FeedItemResponse> items =
        page.stream().map(r -> toItem(r, reactionCounts, myReactions)).toList();

    return new FeedResponse(myCrews, items, nextCursor);
  }

  // crew_id 미지정이면 내 전체 크루.
  // 지정 시 참여 검증: 참여 크루가 아니면 ACCESS_DENIED (크루 존재 여부 밝히지 않음)
  private Collection<Long> resolveScope(Long crewId, Set<Long> myCrewIds) {
    if (crewId == null) {
      return myCrewIds;
    }
    if (!myCrewIds.contains(crewId)) {
      throw new CustomException(CrewErrorCode.CREW_ACCESS_DENIED);
    }
    return List.of(crewId);
  }

  private FeedItemResponse toItem(
      FeedItemRow r,
      Map<Long, Map<String, Long>> reactionCounts,
      Map<Long, List<String>> myReactions) {
    return new FeedItemResponse(
        r.missionLogId(),
        r.crewId(),
        r.crewTitle(),
        r.crewParticipantId(),
        r.memberUuid(),
        r.nickname(),
        resolveUrl(r.profileImageS3Key()),
        resolveUrl(r.imageS3Key()),
        r.caption(),
        SeoulDateTimeUtils.toSeoulOffset(r.serverTime()),
        r.certificationStatus(),
        reactionCounts.getOrDefault(r.missionLogId(), Map.of()),
        myReactions.getOrDefault(r.missionLogId(), List.of()));
  }

  private String resolveUrl(String s3Key) {
    if (s3Key == null) {
      return null;
    }
    return imageDeliveryPort.createDeliveryUrl(new ImageObjectKey(s3Key), IMAGE_URL_TTL).url();
  }

  private Map<Long, Map<String, Long>> buildReactionCounts(List<ReactionRow> reactions) {
    return reactions.stream()
        .collect(
            groupingBy(
                ReactionRow::missionLogId, groupingBy(ReactionRow::reactionType, counting())));
  }

  private Map<Long, List<String>> buildMyReactions(List<ReactionRow> reactions, UUID memberUuid) {
    return reactions.stream()
        .filter(row -> row.memberUuid().equals(memberUuid))
        .collect(
            groupingBy(ReactionRow::missionLogId, mapping(ReactionRow::reactionType, toList())));
  }

  private int clampLimit(Integer limit) {
    if (limit == null) {
      return DEFAULT_LIMIT;
    }
    return Math.max(1, Math.min(limit, MAX_LIMIT));
  }

  // cursor 포맷: {server_time(OffsetDateTime)}_{mission_log_id}
  private Cursor parseCursor(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return new Cursor(null, null);
    }
    int sep = cursor.lastIndexOf('_');
    if (sep < 0) {
      throw new CustomException(MissionErrorCode.INVALID_CURSOR);
    }
    try {
      LocalDateTime serverTime = OffsetDateTime.parse(cursor.substring(0, sep)).toLocalDateTime();
      long id = Long.parseLong(cursor.substring(sep + 1));
      return new Cursor(serverTime, id);
    } catch (RuntimeException e) {
      throw new CustomException(MissionErrorCode.INVALID_CURSOR);
    }
  }

  private String encodeCursor(FeedItemRow last) {
    return SeoulDateTimeUtils.toSeoulOffset(last.serverTime()) + "_" + last.missionLogId();
  }

  private record Cursor(LocalDateTime serverTime, Long id) {}
}
