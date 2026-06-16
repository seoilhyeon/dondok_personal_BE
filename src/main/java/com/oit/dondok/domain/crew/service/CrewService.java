package com.oit.dondok.domain.crew.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oit.dondok.domain.crew.dto.request.CrewCreateRequest;
import com.oit.dondok.domain.crew.dto.request.HostAgreementRequest;
import com.oit.dondok.domain.crew.dto.response.ApplicationListResponse;
import com.oit.dondok.domain.crew.dto.response.CrewCreateResponse;
import com.oit.dondok.domain.crew.dto.response.CrewDetailResponse;
import com.oit.dondok.domain.crew.dto.response.CrewDisbandResponse;
import com.oit.dondok.domain.crew.dto.response.CrewListResponse;
import com.oit.dondok.domain.crew.dto.response.CrewMemberResponse;
import com.oit.dondok.domain.crew.dto.response.CrewMembersResponse;
import com.oit.dondok.domain.crew.dto.response.CrewSummaryResponse;
import com.oit.dondok.domain.crew.dto.response.MyParticipationResponse;
import com.oit.dondok.domain.crew.dto.response.ParticipationApplyResponse;
import com.oit.dondok.domain.crew.dto.response.ParticipationApproveResponse;
import com.oit.dondok.domain.crew.dto.response.ParticipationCancelResponse;
import com.oit.dondok.domain.crew.dto.response.ParticipationCountResponse;
import com.oit.dondok.domain.crew.dto.response.ParticipationRejectResponse;
import com.oit.dondok.domain.crew.dto.response.ParticipationSummaryResponse;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewCategory;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.port.CrewPointPort;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.crew.repository.CrewQueryRepository;
import com.oit.dondok.domain.crew.repository.CrewQueryRepository.CrewWithRule;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import com.oit.dondok.domain.image.port.ImageDeliveryPort;
import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.image.port.ImageObjectKeyPolicy;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.entity.MissionScheduleDay;
import com.oit.dondok.domain.mission.repository.MissionRuleRepository;
import com.oit.dondok.domain.mission.repository.MissionScheduleDayRepository;
import com.oit.dondok.domain.notification.port.NotificationPayload;
import com.oit.dondok.domain.notification.port.NotificationSender;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import com.oit.dondok.global.util.CursorCodec;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
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
  private static final Duration IMAGE_URL_TTL = Duration.ofMinutes(10);

  private static final int MAX_LIMIT = 100;
  private static final int MAX_PARTICIPATION_LIMIT = 200;
  private static final int MAX_MEMBERS_LIMIT = 200;

  private final CrewRepository crewRepository;
  private final CrewParticipantRepository crewParticipantRepository;
  private final MissionRuleRepository missionRuleRepository;
  private final MissionScheduleDayRepository missionScheduleDayRepository;
  private final MemberRepository memberRepository;
  private final CrewPointPort crewPointPort;
  private final CrewQueryRepository crewQueryRepository;
  private final SettlementRepository settlementRepository;
  private final ObjectMapper objectMapper;
  private final ImageDeliveryPort imageDeliveryPort;
  private final ImageObjectKeyPolicy keyPolicy;
  private final NotificationSender notificationSender;

  @Transactional
  public CrewCreateResponse createCrew(UUID memberUuid, CrewCreateRequest request) {
    Member member =
        memberRepository
            .findByUuid(memberUuid)
            .orElseThrow(() -> new CustomException(CrewErrorCode.MEMBER_NOT_FOUND));

    String normalizedCategory = normalizeCategory(request.category());
    validateDepositAmount(request.depositAmount());

    int effectiveMinParticipants =
        request.minParticipants() != null ? request.minParticipants() : DEFAULT_MIN_PARTICIPANTS;

    validateParticipantRange(effectiveMinParticipants, request.maxParticipants());
    validateFrequencyRule(request.frequencyType(), request.missionScheduleDays());
    validateDateRange(request.recruitmentDeadline(), request.startDate(), request.endDate());
    String imageS3Key = requireOwnedCrewImageKey(memberUuid, request.imageS3Key());

    LocalDateTime recruitmentDeadlineLdt =
        request.recruitmentDeadline().atZoneSameInstant(SEOUL_ZONE).toLocalDateTime();
    LocalDateTime hostAgreedAtLdt =
        request.hostAgreement().agreedAt().atZoneSameInstant(SEOUL_ZONE).toLocalDateTime();
    LocalDateTime startAt = request.startDate().atStartOfDay(SEOUL_ZONE).toLocalDateTime();
    LocalDateTime endAt = request.endDate().atTime(23, 59, 59);

    String hostAgreementSnapshot = serializeHostAgreement(request.hostAgreement());

    Crew crew =
        crewRepository.save(
            Crew.create(
                member,
                request.title(),
                request.description(),
                imageS3Key,
                normalizedCategory,
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

  private String normalizeCategory(String category) {
    if (!StringUtils.hasText(category)) {
      return null;
    }
    try {
      return CrewCategory.valueOf(category.toUpperCase(Locale.ROOT)).name();
    } catch (IllegalArgumentException e) {
      throw new CustomException(CrewErrorCode.INVALID_CATEGORY);
    }
  }

  // 클라이언트가 제출한 crew 이미지 key가 본인(memberUuid) 네임스페이스의 서버 발급 crew key인지 검증한다.
  private String requireOwnedCrewImageKey(UUID memberUuid, String imageS3Key) {
    if (!StringUtils.hasText(imageS3Key)) {
      return null;
    }
    if (!keyPolicy.matchesCrewKey(memberUuid, imageS3Key)) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
    return imageS3Key;
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

  @Transactional
  public ParticipationApplyResponse applyParticipation(Long crewId, UUID memberUuid) {
    Crew crew =
        crewRepository
            .findByIdWithOptimisticLock(crewId)
            .orElseThrow(() -> new CustomException(CrewErrorCode.CREW_NOT_FOUND));

    if (crew.getStatus() != CrewStatus.RECRUITING
        || !LocalDateTime.now(SEOUL_ZONE).isBefore(crew.getRecruitmentDeadline())) {
      throw new CustomException(CrewErrorCode.CREW_NOT_RECRUITING);
    }

    Optional<CrewParticipant> existingOpt =
        crewParticipantRepository.findByCrewIdAndMemberUuid(crewId, memberUuid);

    CrewParticipant participant;

    if (existingOpt.isPresent()) {
      CrewParticipant existing = existingOpt.get();
      CrewParticipantStatus existingStatus = existing.getStatus();
      if (existingStatus == CrewParticipantStatus.PENDING
          || existingStatus == CrewParticipantStatus.LOCKED) {
        throw new CustomException(CrewErrorCode.ALREADY_PARTICIPATING);
      }
      if (existingStatus == CrewParticipantStatus.REJECTED
          || existingStatus == CrewParticipantStatus.EXPIRED) {
        throw new CustomException(CrewErrorCode.APPLICATION_NOT_ALLOWED);
      }
      // CANCELLED → reopen: Member 엔티티 불필요
      checkCapacity(crewId, crew.getMaxParticipants());
      existing.reopen(LocalDateTime.now(SEOUL_ZONE));
      try {
        crewParticipantRepository.saveAndFlush(existing);
      } catch (OptimisticLockingFailureException e) {
        throw new CustomException(CrewErrorCode.CONCURRENT_PAYMENT_ERROR, e);
      }
      participant = existing;
    } else {
      Member member =
          memberRepository
              .findByUuid(memberUuid)
              .orElseThrow(() -> new CustomException(CrewErrorCode.MEMBER_NOT_FOUND));
      checkCapacity(crewId, crew.getMaxParticipants());
      try {
        participant =
            crewParticipantRepository.saveAndFlush(
                CrewParticipant.createPending(
                    crew, member, crew.getDepositAmount(), LocalDateTime.now(SEOUL_ZONE)));
      } catch (DataIntegrityViolationException e) {
        // uk_crew_participant_crew_member 위반: 동시 신청으로 이미 row 생성됨
        throw new CustomException(CrewErrorCode.ALREADY_PARTICIPATING, e);
      } catch (OptimisticLockingFailureException e) {
        // crew 버전 충돌: 동시 신청으로 정원이 찼거나 crew 상태가 변경됨
        throw new CustomException(CrewErrorCode.CONCURRENT_PAYMENT_ERROR, e);
      }
    }

    crewPointPort.reserveForPendingParticipant(participant);
    notificationSender.send(
        crew.getHostMember(),
        new NotificationPayload(
            "CREW_APPLICATION_RECEIVED",
            "crew",
            String.valueOf(crewId),
            "dondok://crews/" + crewId,
            participant.getMember().getNickname() + "님이 크루 참여를 신청했습니다."));
    return ParticipationApplyResponse.from(participant, crewId, memberUuid);
  }

  @Transactional
  public ParticipationCancelResponse cancelParticipation(Long crewId, UUID memberUuid) {
    Crew crew =
        crewRepository
            .findById(crewId)
            .orElseThrow(() -> new CustomException(CrewErrorCode.CREW_NOT_FOUND));

    CrewParticipant participant =
        crewParticipantRepository
            .findByCrewIdAndMemberUuid(crewId, memberUuid)
            .orElseThrow(() -> new CustomException(CrewErrorCode.PARTICIPANT_NOT_FOUND));

    if (participant.getStatus() != CrewParticipantStatus.PENDING) {
      throw new CustomException(CrewErrorCode.APPLICATION_NOT_CANCELLABLE);
    }

    participant.cancel(LocalDateTime.now(SEOUL_ZONE));
    try {
      crewParticipantRepository.saveAndFlush(participant);
    } catch (OptimisticLockingFailureException e) {
      throw new CustomException(CrewErrorCode.CONCURRENT_PAYMENT_ERROR, e);
    }
    crewPointPort.releasePendingReserve(participant);
    notificationSender.send(
        crew.getHostMember(),
        new NotificationPayload(
            "CREW_APPLICATION_CANCELLED",
            "crew",
            String.valueOf(crewId),
            "dondok://crews/" + crewId,
            participant.getMember().getNickname() + "님이 크루 참여 신청을 취소했습니다."));
    return ParticipationCancelResponse.of(participant, crewId);
  }

  private void checkCapacity(Long crewId, int maxParticipants) {
    long count =
        crewParticipantRepository.countByCrewIdAndStatusIn(
            crewId, List.of(CrewParticipantStatus.PENDING, CrewParticipantStatus.LOCKED));
    if (count >= maxParticipants) {
      throw new CustomException(CrewErrorCode.CAPACITY_FULL);
    }
  }

  private CrewParticipant saveHostParticipant(Crew crew, Member member, Long depositAmount) {
    try {
      return crewParticipantRepository.saveAndFlush(
          CrewParticipant.create(crew, member, depositAmount, LocalDateTime.now(SEOUL_ZONE)));
    } catch (OptimisticLockingFailureException e) {
      throw new CustomException(CrewErrorCode.CONCURRENT_PAYMENT_ERROR, e);
    }
  }

  @Transactional(readOnly = true)
  public CrewDetailResponse findCrewDetail(Long crewId, UUID memberUuid) {
    Crew crew =
        crewQueryRepository
            .findCrewWithHost(crewId)
            .orElseThrow(() -> new CustomException(CrewErrorCode.CREW_NOT_FOUND));

    MissionRule missionRule =
        missionRuleRepository
            .findByCrewId(crewId)
            .orElseThrow(() -> new CustomException(CrewErrorCode.CREW_NOT_FOUND));

    List<String> scheduleDays;
    if (missionRule.getFrequencyType() == MissionFrequencyType.SPECIFIC_DAYS) {
      Map<Long, List<String>> daysMap =
          crewQueryRepository.findScheduleDaysByRuleIds(List.of(missionRule.getId()));
      scheduleDays = daysMap.getOrDefault(missionRule.getId(), List.of());
    } else {
      scheduleDays = List.of();
    }

    String settlementStatus =
        settlementRepository.findByCrewId(crewId).map(s -> s.getStatus().name()).orElse("NONE");

    MyParticipationResponse myParticipation =
        crewParticipantRepository
            .findByCrewIdAndMemberUuid(crewId, memberUuid)
            .map(MyParticipationResponse::from)
            .orElse(null);

    String hostNickname = crew.getHostMember().getNickname();
    int currentParticipants =
        (int)
            crewParticipantRepository.countByCrewIdAndStatusIn(
                crewId, List.of(CrewParticipantStatus.PENDING, CrewParticipantStatus.LOCKED));

    return CrewDetailResponse.of(
        crew,
        missionRule,
        scheduleDays,
        settlementStatus,
        myParticipation,
        resolveImageUrl(crew.getImageS3Key()),
        hostNickname,
        currentParticipants);
  }

  @Transactional(readOnly = true)
  public CrewListResponse findCrewList(
      CrewStatus status, String category, String keyword, String cursor, int limit) {
    int effectiveLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
    Long cursorId = CursorCodec.decode(cursor);
    String normalizedCategory = normalizeCategory(category);

    List<CrewWithRule> rows =
        crewQueryRepository.findCrewsWithRule(
            status, normalizedCategory, keyword, cursorId, effectiveLimit);

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
        hasNext ? CursorCodec.encode(pageRows.get(pageRows.size() - 1).crew().getId()) : null;

    return new CrewListResponse(items, nextCursor);
  }

  @Transactional(readOnly = true)
  public CrewMembersResponse findCrewMembers(
      Long crewId, UUID memberUuid, String cursor, int limit) {
    Crew crew =
        crewQueryRepository
            .findCrewWithHost(crewId)
            .orElseThrow(() -> new CustomException(CrewErrorCode.CREW_NOT_FOUND));

    boolean isHost = crew.getHostMember().getUuid().equals(memberUuid);
    if (!isHost) {
      crewParticipantRepository
          .findByCrewIdAndMemberUuid(crewId, memberUuid)
          .filter(p -> p.getStatus() == CrewParticipantStatus.LOCKED)
          .orElseThrow(() -> new CustomException(CrewErrorCode.CREW_ACCESS_DENIED));
    }

    int effectiveLimit = Math.min(Math.max(limit, 1), MAX_MEMBERS_LIMIT);
    Long cursorId = CursorCodec.decode(cursor);

    List<CrewParticipant> rows =
        crewParticipantRepository.findByCrewIdAndStatusAndIdGreaterThanOrderByIdAsc(
            crewId,
            CrewParticipantStatus.LOCKED,
            cursorId == null ? 0L : cursorId,
            PageRequest.of(0, effectiveLimit + 1));

    boolean hasNext = rows.size() > effectiveLimit;
    List<CrewParticipant> pageRows = hasNext ? rows.subList(0, effectiveLimit) : rows;

    UUID hostUuid = crew.getHostMember().getUuid();
    List<CrewMemberResponse> items =
        pageRows.stream()
            .map(
                p ->
                    CrewMemberResponse.from(
                        p, hostUuid, resolveImageUrl(p.getMember().getProfileImageS3Key())))
            .toList();

    String nextCursor =
        hasNext ? CursorCodec.encode(pageRows.get(pageRows.size() - 1).getId()) : null;
    return new CrewMembersResponse(items, nextCursor);
  }

  @Transactional
  public ParticipationApproveResponse approveParticipation(
      Long crewId, Long participantId, UUID memberUuid) {
    validateHostCrew(crewId, memberUuid);
    CrewParticipant participant = requireParticipantInCrew(crewId, participantId);

    if (participant.getStatus() != CrewParticipantStatus.PENDING) {
      throw new CustomException(CrewErrorCode.APPLICATION_NOT_APPROVABLE);
    }

    participant.lock(LocalDateTime.now(SEOUL_ZONE));
    try {
      crewParticipantRepository.saveAndFlush(participant);
    } catch (OptimisticLockingFailureException e) {
      throw new CustomException(CrewErrorCode.CONCURRENT_PAYMENT_ERROR, e);
    }
    crewPointPort.lockForApprovedParticipant(participant);
    notificationSender.send(
        participant.getMember(),
        new NotificationPayload(
            "CREW_APPLICATION_APPROVED",
            "crew",
            String.valueOf(crewId),
            "dondok://crews/" + crewId,
            "'" + participant.getCrew().getTitle() + "' 크루 참여 신청이 승인되었습니다."));
    return ParticipationApproveResponse.from(participant);
  }

  @Transactional
  public ParticipationRejectResponse rejectParticipation(
      Long crewId, Long participantId, UUID memberUuid) {
    validateHostCrew(crewId, memberUuid);
    CrewParticipant participant = requireParticipantInCrew(crewId, participantId);

    if (participant.getStatus() != CrewParticipantStatus.PENDING) {
      throw new CustomException(CrewErrorCode.APPLICATION_NOT_REJECTABLE);
    }

    participant.reject(LocalDateTime.now(SEOUL_ZONE));
    try {
      crewParticipantRepository.saveAndFlush(participant);
    } catch (OptimisticLockingFailureException e) {
      throw new CustomException(CrewErrorCode.CONCURRENT_PAYMENT_ERROR, e);
    }
    crewPointPort.releasePendingReserve(participant);
    notificationSender.send(
        participant.getMember(),
        new NotificationPayload(
            "CREW_APPLICATION_REJECTED",
            "crew",
            String.valueOf(crewId),
            "dondok://crews/" + crewId,
            "'" + participant.getCrew().getTitle() + "' 크루 참여 신청이 거절되었습니다."));
    return ParticipationRejectResponse.from(participant);
  }

  @Transactional(readOnly = true)
  public ApplicationListResponse getParticipationList(
      Long crewId, CrewParticipantStatus status, UUID memberUuid, String cursor, int limit) {
    validateHostCrew(crewId, memberUuid);

    int effectiveLimit = Math.min(Math.max(limit, 1), MAX_PARTICIPATION_LIMIT);
    Long cursorId = CursorCodec.decode(cursor);

    List<CrewParticipant> rows =
        crewParticipantRepository.findByCrewIdAndStatusAndIdGreaterThanOrderByIdAsc(
            crewId,
            status,
            cursorId == null ? 0L : cursorId,
            PageRequest.of(0, effectiveLimit + 1));

    boolean hasNext = rows.size() > effectiveLimit;
    List<CrewParticipant> pageRows = hasNext ? rows.subList(0, effectiveLimit) : rows;

    List<ParticipationSummaryResponse> items =
        pageRows.stream().map(ParticipationSummaryResponse::from).toList();

    String nextCursor =
        hasNext ? CursorCodec.encode(pageRows.get(pageRows.size() - 1).getId()) : null;

    return new ApplicationListResponse(items, nextCursor);
  }

  @Transactional(readOnly = true)
  public ParticipationCountResponse getParticipationCount(Long crewId, UUID memberUuid) {
    validateHostCrew(crewId, memberUuid);

    long pending =
        crewParticipantRepository.countByCrewIdAndStatus(crewId, CrewParticipantStatus.PENDING);
    long locked =
        crewParticipantRepository.countByCrewIdAndStatus(crewId, CrewParticipantStatus.LOCKED);
    long rejected =
        crewParticipantRepository.countByCrewIdAndStatus(crewId, CrewParticipantStatus.REJECTED);

    return ParticipationCountResponse.of(pending, locked, rejected);
  }

  private String resolveImageUrl(String imageS3Key) {
    if (!StringUtils.hasText(imageS3Key)) {
      return null;
    }
    // 발급 실패는 격리하지 않고 전파한다(profile 경로와 동일). 설정/보안 오류는 GlobalExceptionHandler에서
    // 중앙 로깅되고, 표시 URL을 만들 수 없으면 응답을 성공으로 위장하지 않는다.
    return imageDeliveryPort.createDeliveryUrl(new ImageObjectKey(imageS3Key), IMAGE_URL_TTL).url();
  }

  @Transactional
  public CrewDisbandResponse disbandCrew(Long crewId, UUID memberUuid) {
    validateHostCrew(crewId, memberUuid);

    Crew crew =
        crewRepository
            .findByIdWithOptimisticLock(crewId)
            .orElseThrow(() -> new CustomException(CrewErrorCode.CREW_NOT_FOUND));

    LocalDateTime now = LocalDateTime.now(SEOUL_ZONE);
    crew.disband(now);
    crewRepository.saveAndFlush(crew);

    List<CrewParticipant> pendingParticipants =
        crewParticipantRepository.findByCrewIdAndStatus(crewId, CrewParticipantStatus.PENDING);
    for (CrewParticipant p : pendingParticipants) {
      p.cancelOnCrewCancelled(now);
      crewParticipantRepository.save(p);
      crewPointPort.releasePendingReserve(p);
    }

    List<CrewParticipant> lockedParticipants =
        crewParticipantRepository.findByCrewIdAndStatus(crewId, CrewParticipantStatus.LOCKED);
    for (CrewParticipant p : lockedParticipants) {
      p.cancelOnCrewCancelled(now);
      crewParticipantRepository.save(p);
      crewPointPort.releaseLockedDepositForCancelledCrew(p);
      notificationSender.send(
          p.getMember(),
          new NotificationPayload(
              "CREW_DISBANDED",
              "crew",
              String.valueOf(crewId),
              "dondok://crews/" + crewId,
              "'" + crew.getTitle() + "' 크루가 해체되었습니다. 보증금이 전액 환급됩니다."));
    }

    return CrewDisbandResponse.of(crew);
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

  private CrewParticipant requireParticipantInCrew(Long crewId, Long participantId) {
    CrewParticipant participant =
        crewParticipantRepository
            .findById(participantId)
            .orElseThrow(() -> new CustomException(CrewErrorCode.PARTICIPANT_NOT_FOUND));
    if (!participant.getCrew().getId().equals(crewId)) {
      throw new CustomException(CrewErrorCode.PARTICIPANT_NOT_IN_CREW);
    }
    return participant;
  }
}
