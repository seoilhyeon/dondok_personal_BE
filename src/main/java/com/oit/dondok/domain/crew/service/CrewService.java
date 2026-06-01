package com.oit.dondok.domain.crew.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oit.dondok.domain.crew.dto.request.CrewCreateRequest;
import com.oit.dondok.domain.crew.dto.request.HostAgreementRequest;
import com.oit.dondok.domain.crew.dto.response.CrewCreateResponse;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.port.CrewPointPort;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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

  private final CrewRepository crewRepository;
  private final CrewParticipantRepository crewParticipantRepository;
  private final MissionRuleRepository missionRuleRepository;
  private final MissionScheduleDayRepository missionScheduleDayRepository;
  private final MemberRepository memberRepository;
  private final CrewPointPort crewPointPort;
  private final ObjectMapper objectMapper;

  @Transactional
  public CrewCreateResponse createCrew(Long memberId, CrewCreateRequest request) {
    Member member =
        memberRepository
            .findById(memberId)
            .orElseThrow(() -> new CustomException(CrewErrorCode.MEMBER_NOT_FOUND));

    validateDepositAmount(request.depositAmount());
    validateFrequencyRule(request.frequencyType(), request.missionScheduleDays());

    LocalDateTime recruitmentDeadlineLdt =
        request.recruitmentDeadline().atZoneSameInstant(SEOUL_ZONE).toLocalDateTime();
    LocalDateTime hostAgreedAtLdt =
        request.hostAgreement().agreedAt().atZoneSameInstant(SEOUL_ZONE).toLocalDateTime();
    LocalDateTime startAt = request.startDate().atStartOfDay();
    LocalDateTime endAt = request.endDate().atStartOfDay();

    String hostAgreementSnapshot = serializeHostAgreement(request.hostAgreement());

    int effectiveMinParticipants =
        request.minParticipants() != null ? request.minParticipants() : DEFAULT_MIN_PARTICIPANTS;

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

    List<Integer> scheduleDays = saveScheduleDays(missionRule, request);

    CrewParticipant participant = saveHostParticipant(crew, member, request.depositAmount());
    crewPointPort.lockForHostParticipant(participant);

    String imageUrl = resolveImageUrl(crew.getImageS3Key());
    return CrewCreateResponse.of(crew, missionRule, scheduleDays, participant, imageUrl);
  }

  private void validateDepositAmount(Long depositAmount) {
    if (depositAmount < MIN_DEPOSIT || depositAmount > MAX_DEPOSIT || depositAmount % 1_000 != 0) {
      throw new CustomException(CrewErrorCode.INVALID_DEPOSIT_AMOUNT);
    }
  }

  private void validateFrequencyRule(
      MissionFrequencyType frequencyType, List<Integer> missionScheduleDays) {
    if (frequencyType == MissionFrequencyType.SPECIFIC_DAYS
        && (missionScheduleDays == null || missionScheduleDays.isEmpty())) {
      throw new CustomException(CrewErrorCode.INVALID_FREQUENCY_RULE);
    }
  }

  private String serializeHostAgreement(HostAgreementRequest hostAgreement) {
    try {
      return objectMapper.writeValueAsString(hostAgreement);
    } catch (JsonProcessingException e) {
      throw new CustomException(GlobalErrorCode.SERVER_ERROR, e);
    }
  }

  private List<Integer> saveScheduleDays(MissionRule missionRule, CrewCreateRequest request) {
    if (request.frequencyType() != MissionFrequencyType.SPECIFIC_DAYS) {
      return List.of();
    }
    List<Integer> days = new ArrayList<>(request.missionScheduleDays());
    for (Integer day : days) {
      missionScheduleDayRepository.save(MissionScheduleDay.create(missionRule, day));
    }
    return days;
  }

  private CrewParticipant saveHostParticipant(Crew crew, Member member, Long depositAmount) {
    try {
      return crewParticipantRepository.saveAndFlush(
          CrewParticipant.create(crew, member, depositAmount, LocalDateTime.now()));
    } catch (OptimisticLockingFailureException e) {
      throw new CustomException(CrewErrorCode.CONCURRENT_PAYMENT_ERROR, e);
    }
  }

  private String resolveImageUrl(String imageS3Key) {
    if (!StringUtils.hasText(imageS3Key)) {
      return null;
    }
    // TODO: Generate a short-lived presigned GET URL after S3 image URL resolver is added.
    return null;
  }
}
