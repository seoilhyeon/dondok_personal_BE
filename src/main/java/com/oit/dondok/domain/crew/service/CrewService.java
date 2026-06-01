package com.oit.dondok.domain.crew.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oit.dondok.domain.crew.dto.request.CrewCreateRequest;
import com.oit.dondok.domain.crew.dto.request.HostAgreementRequest;
import com.oit.dondok.domain.crew.dto.response.CrewCreateResponse;
import com.oit.dondok.domain.crew.dto.response.CrewListResponse;
import com.oit.dondok.domain.crew.dto.response.CrewSummaryResponse;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewCategory;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.port.CrewPointPort;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.crew.repository.CrewQueryRepository;
import com.oit.dondok.domain.crew.repository.CrewQueryRepository.CrewWithRule;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.entity.MissionScheduleDay;
import com.oit.dondok.domain.mission.repository.MissionRuleRepository;
import com.oit.dondok.domain.mission.repository.MissionScheduleDayRepository;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CrewService {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
  private static final long MIN_DEPOSIT = 1_000L;
  private static final long MAX_DEPOSIT = 100_000L;
  private static final int DEFAULT_MIN_PARTICIPANTS = 2;

  private static final int MAX_LIMIT = 100;

  private final CrewRepository crewRepository;
  private final CrewParticipantRepository crewParticipantRepository;
  private final MissionRuleRepository missionRuleRepository;
  private final MissionScheduleDayRepository missionScheduleDayRepository;
  private final MemberRepository memberRepository;
  private final CrewPointPort crewPointPort;
  private final CrewQueryRepository crewQueryRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public CrewCreateResponse createCrew(UUID memberUuid, CrewCreateRequest request) {
    Member member =
        memberRepository
            .findByUuid(memberUuid)
            .orElseThrow(() -> new CustomException(CrewErrorCode.MEMBER_NOT_FOUND));

    validateDepositAmount(request.depositAmount());

    int effectiveMinParticipants =
        request.minParticipants() != null ? request.minParticipants() : DEFAULT_MIN_PARTICIPANTS;

    validateParticipantRange(effectiveMinParticipants, request.maxParticipants());
    validateFrequencyRule(request.frequencyType(), request.missionScheduleDays());
    validateDateRange(request.recruitmentDeadline(), request.startDate(), request.endDate());

    LocalDateTime recruitmentDeadlineLdt =
        request.recruitmentDeadline().atZoneSameInstant(SEOUL_ZONE).toLocalDateTime();
    LocalDateTime hostAgreedAtLdt =
        request.hostAgreement().agreedAt().atZoneSameInstant(SEOUL_ZONE).toLocalDateTime();
    LocalDateTime startAt = request.startDate().atStartOfDay();
    LocalDateTime endAt = request.endDate().atTime(23, 59, 59);

    String hostAgreementSnapshot = serializeHostAgreement(request.hostAgreement());

    // TODO: IMG-001 완료 후 S3 presigned URL 흐름 검증 추가 필요
    // 현재는 클라이언트가 넘긴 key를 그대로 저장함
    Crew crew =
        crewRepository.save(
            Crew.create(
                member,
                request.title(),
                request.description(),
                request.imageS3Key(),
                request.category(),
                hostAgreementSnapshot,
                request.hostAgreement().version(),
                hostAgreedAtLdt,
                request.depositAmount(),
                effectiveMinParticipants,
                request.maxParticipants(),
                recruitmentDeadlineLdt,
                startAt,
                endAt));

    MissionRule missionRule =
        missionRuleRepository.save(
            MissionRule.create(crew, request.frequencyType(), request.dailySettlementType()));

    List<String> scheduleDayNames = saveScheduleDays(missionRule, request);

    CrewParticipant participant = saveHostParticipant(crew, member, request.depositAmount());
    crewPointPort.lockForHostParticipant(participant);

    String imageUrl = resolveImageUrl(crew.getImageS3Key());
    return CrewCreateResponse.of(crew, missionRule, scheduleDayNames, participant, imageUrl);
  }

  private void validateDepositAmount(Long depositAmount) {
    if (depositAmount < MIN_DEPOSIT || depositAmount > MAX_DEPOSIT || depositAmount % 1_000 != 0) {
      throw new CustomException(CrewErrorCode.INVALID_DEPOSIT_AMOUNT);
    }
  }

  private void validateParticipantRange(int minParticipants, int maxParticipants) {
    if (minParticipants > maxParticipants) {
      throw new CustomException(CrewErrorCode.VALIDATION_ERROR);
    }
  }

  private void validateFrequencyRule(
      MissionFrequencyType frequencyType, List<String> missionScheduleDays) {
    if (frequencyType != MissionFrequencyType.SPECIFIC_DAYS) {
      return;
    }
    if (missionScheduleDays == null || missionScheduleDays.isEmpty()) {
      throw new CustomException(CrewErrorCode.INVALID_FREQUENCY_RULE);
    }
    HashSet<String> seen = new HashSet<>();
    for (String day : missionScheduleDays) {
      if (day == null) {
        throw new CustomException(CrewErrorCode.INVALID_FREQUENCY_RULE);
      }
      try {
        DayOfWeek.valueOf(day.toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new CustomException(CrewErrorCode.INVALID_FREQUENCY_RULE);
      }
      if (!seen.add(day.toUpperCase())) {
        throw new CustomException(CrewErrorCode.INVALID_FREQUENCY_RULE);
      }
    }
  }

  private void validateDateRange(
      OffsetDateTime recruitmentDeadline, LocalDate startDate, LocalDate endDate) {
    if (!startDate.isBefore(endDate)) {
      throw new CustomException(CrewErrorCode.VALIDATION_ERROR);
    }
    OffsetDateTime startAtOffset = startDate.atStartOfDay(SEOUL_ZONE).toOffsetDateTime();
    if (!recruitmentDeadline.isBefore(startAtOffset)) {
      throw new CustomException(CrewErrorCode.VALIDATION_ERROR);
    }
  }

  private String serializeHostAgreement(HostAgreementRequest hostAgreement) {
    try {
      return objectMapper.writeValueAsString(hostAgreement);
    } catch (JsonProcessingException e) {
      throw new CustomException(GlobalErrorCode.SERVER_ERROR, e);
    }
  }

  private List<String> saveScheduleDays(MissionRule missionRule, CrewCreateRequest request) {
    if (request.frequencyType() != MissionFrequencyType.SPECIFIC_DAYS) {
      return List.of();
    }
    List<String> dayNames = List.copyOf(request.missionScheduleDays());
    for (String dayName : dayNames) {
      int dayOfWeek = DayOfWeek.valueOf(dayName.toUpperCase()).getValue();
      missionScheduleDayRepository.save(MissionScheduleDay.create(missionRule, dayOfWeek));
    }
    return dayNames;
  }

  private CrewParticipant saveHostParticipant(Crew crew, Member member, Long depositAmount) {
    try {
      return crewParticipantRepository.saveAndFlush(
          CrewParticipant.create(crew, member, depositAmount, LocalDateTime.now()));
    } catch (OptimisticLockingFailureException e) {
      throw new CustomException(CrewErrorCode.CONCURRENT_PAYMENT_ERROR, e);
    }
  }

  @Transactional(readOnly = true)
  public CrewListResponse findCrewList(
      CrewStatus status, CrewCategory category, String keyword, String cursor, int limit) {
    int effectiveLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
    Long cursorId = decodeCursor(cursor);

    List<CrewWithRule> rows =
        crewQueryRepository.findCrewsWithRule(status, category, keyword, cursorId, effectiveLimit);

    boolean hasNext = rows.size() > effectiveLimit;
    List<CrewWithRule> pageRows = hasNext ? rows.subList(0, effectiveLimit) : rows;

    List<Long> specificDaysRuleIds =
        pageRows.stream()
            .filter(r -> r.missionRule().getFrequencyType() == MissionFrequencyType.SPECIFIC_DAYS)
            .map(r -> r.missionRule().getId())
            .toList();

    Map<Long, List<String>> scheduleDaysMap =
        crewQueryRepository.findScheduleDaysByRuleIds(specificDaysRuleIds);

    List<CrewSummaryResponse> items =
        pageRows.stream()
            .map(
                r -> {
                  MissionRule rule = r.missionRule();
                  List<String> days =
                      rule.getFrequencyType() == MissionFrequencyType.SPECIFIC_DAYS
                          ? scheduleDaysMap.getOrDefault(rule.getId(), List.of())
                          : List.of();
                  return CrewSummaryResponse.of(
                      r.crew(), rule, days, resolveImageUrl(r.crew().getImageS3Key()));
                })
            .toList();

    String nextCursor =
        hasNext ? encodeCursor(pageRows.get(pageRows.size() - 1).crew().getId()) : null;

    return new CrewListResponse(items, nextCursor);
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

  private static String encodeCursor(Long crewId) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(String.valueOf(crewId).getBytes(StandardCharsets.UTF_8));
  }

  private String resolveImageUrl(String imageS3Key) {
    if (!StringUtils.hasText(imageS3Key)) {
      return null;
    }
    // TODO: Generate a short-lived presigned GET URL after S3 image URL resolver is added.
    return null;
  }
}
