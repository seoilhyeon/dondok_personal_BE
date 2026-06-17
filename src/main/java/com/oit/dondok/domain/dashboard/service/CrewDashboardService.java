package com.oit.dondok.domain.dashboard.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.crew.repository.CrewQueryRepository;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import com.oit.dondok.domain.dashboard.dto.response.CrewDashboardParticipantResponse;
import com.oit.dondok.domain.dashboard.dto.response.CrewDashboardResponse;
import com.oit.dondok.domain.dashboard.dto.response.CrewDashboardResponse.ProjectionNotice;
import com.oit.dondok.domain.dashboard.dto.response.CrewDashboardResponse.ProjectionStatus;
import com.oit.dondok.domain.dashboard.repository.CrewDashboardParticipantRow;
import com.oit.dondok.domain.dashboard.repository.CrewDashboardQueryRepository;
import com.oit.dondok.domain.dashboard.repository.CrewDashboardSnapshotRow;
import com.oit.dondok.domain.dashboard.repository.CrewParticipantRosterRow;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.exception.MissionErrorCode;
import com.oit.dondok.domain.mission.repository.MissionRuleRepository;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementStatus;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.util.SeoulDateTimeUtils;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CrewDashboardService {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final int RECENT_SNAPSHOT_LIMIT = 2;

  private final CrewRepository crewRepository;
  private final CrewParticipantRepository crewParticipantRepository;
  private final MissionRuleRepository missionRuleRepository;
  private final CrewQueryRepository crewQueryRepository;
  private final SettlementRepository settlementRepository;
  private final CrewDashboardQueryRepository crewDashboardQueryRepository;

  public CrewDashboardResponse getCrewDashboard(UUID memberUuid, Long crewId) {
    // 인가: 해당 크루 LOCKED 참여자만 통과
    CrewParticipant myParticipant = authorize(memberUuid, crewId);
    Crew crew =
        crewRepository
            .findById(crewId)
            .orElseThrow(() -> new CustomException(CrewErrorCode.CREW_NOT_FOUND));
    MissionRule missionRule =
        missionRuleRepository
            .findByCrewId(crewId)
            .orElseThrow(() -> new CustomException(MissionErrorCode.MISSION_RULE_NOT_FOUND));

    // 최신/직전 PROVISIONAL 스냅샷 + 그 스냅샷들의 전 참여자 행
    List<CrewDashboardSnapshotRow> snapshots =
        crewDashboardQueryRepository.findRecentProvisionalSnapshots(crewId, RECENT_SNAPSHOT_LIMIT);
    CrewDashboardSnapshotRow latest = snapshots.isEmpty() ? null : snapshots.get(0);
    CrewDashboardSnapshotRow previous = snapshots.size() > 1 ? snapshots.get(1) : null;
    Map<Long, List<CrewDashboardParticipantRow>> rowsBySnapshot =
        crewDashboardQueryRepository
            .findParticipantRows(
                snapshots.stream().map(CrewDashboardSnapshotRow::snapshotId).toList())
            .stream()
            .collect(Collectors.groupingBy(CrewDashboardParticipantRow::snapshotId));

    Settlement settlement = settlementRepository.findByCrewId(crewId).orElse(null);
    LocalDateTime now = LocalDateTime.now(SEOUL);
    // 크루 상태 + settlement + 스냅샷 유무로 projection 상태 판정
    ProjectionStatus projectionStatus =
        resolveProjectionStatus(crew.getStatus(), settlement, latest);

    long myParticipantId = myParticipant.getId();
    List<CrewDashboardParticipantRow> latestRows =
        latest == null ? List.of() : rowsBySnapshot.getOrDefault(latest.snapshotId(), List.of());
    List<CrewDashboardParticipantRow> previousRows =
        previous == null
            ? List.of()
            : rowsBySnapshot.getOrDefault(previous.snapshotId(), List.of());

    // LIVE/CLOSED_ESTIMATE에서만 계산 필드를 채운다
    boolean computable =
        projectionStatus == ProjectionStatus.LIVE
            || projectionStatus == ProjectionStatus.CLOSED_ESTIMATE;

    // 최신/직전 스냅샷에서 내 행 (없으면 null → 계산 필드 null degrade)
    CrewDashboardParticipantRow myLatestRow =
        computable ? findRow(latestRows, myParticipantId) : null;
    CrewDashboardParticipantRow myPreviousRow = findRow(previousRows, myParticipantId);

    // rank/환급금은 최신 기준, delta/rank_delta는 직전 대비
    Integer rank = computable && myLatestRow != null ? rankOf(latestRows, myParticipantId) : null;
    Integer rankDelta =
        rank != null && myPreviousRow != null ? rankOf(previousRows, myParticipantId) - rank : null;
    Long myExpected = computable && myLatestRow != null ? myLatestRow.expectedRefundAmount() : null;
    Long myDelta =
        myExpected != null && myPreviousRow != null
            ? myExpected - myPreviousRow.expectedRefundAmount()
            : null;

    // participants/rank_total: NOT_STARTED는 LOCKED 로스터,
    // computable(LIVE/CLOSED_ESTIMATE)은 스냅샷 기준, 그 외는 빈 목록
    List<CrewDashboardParticipantResponse> participants;
    int rankTotal;
    if (projectionStatus == ProjectionStatus.NOT_STARTED) {
      List<CrewParticipantRosterRow> roster =
          crewDashboardQueryRepository.findLockedParticipants(crewId);
      participants = toRosterParticipants(roster, myParticipantId);
      rankTotal = roster.size();
    } else if (computable) {
      participants = toParticipants(latestRows, myParticipantId);
      rankTotal = latestRows.size();
    } else {
      participants = List.of();
      rankTotal = 0;
    }

    // 응답 조립 (settlement_id는 SETTLEMENT_SUCCEEDED에서만, 미산출 필드는 null/빈 목록)
    return new CrewDashboardResponse(
        crew.getId(),
        crew.getTitle(),
        myParticipantId,
        projectionStatus == ProjectionStatus.SETTLEMENT_SUCCEEDED ? settlement.getId() : null,
        crew.getStatus(),
        settlement == null ? "NONE" : settlement.getStatus().name(),
        projectionStatus,
        resolveNotice(projectionStatus, myLatestRow),
        daysUntilEnd(crew, now),
        myParticipant.getDepositAmount(),
        resolveMySuccessCount(projectionStatus, computable, myLatestRow),
        myExpected,
        myDelta,
        rank,
        rankTotal,
        rankDelta,
        resolveNextSettlementAt(crew, missionRule, now),
        participants,
        latest == null
            ? SeoulDateTimeUtils.toSeoulOffset(now)
            : SeoulDateTimeUtils.toSeoulOffset(latest.frozenAt()));
  }

  // 크루 없음→CREW_NOT_FOUND / 참여 행 없음→PARTICIPANT_NOT_FOUND / LOCKED 아님→CREW_ACCESS_DENIED
  private CrewParticipant authorize(UUID memberUuid, Long crewId) {
    CrewParticipant participant =
        crewParticipantRepository.findByCrewIdAndMemberUuid(crewId, memberUuid).orElse(null);
    if (participant == null) {
      if (!crewRepository.existsById(crewId)) {
        throw new CustomException(CrewErrorCode.CREW_NOT_FOUND);
      }
      throw new CustomException(CrewErrorCode.PARTICIPANT_NOT_FOUND);
    }
    if (participant.getStatus() != CrewParticipantStatus.LOCKED) {
      throw new CustomException(CrewErrorCode.CREW_ACCESS_DENIED);
    }
    return participant;
  }

  // SETTLEMENT_SUCCEEDED > CANCELLED(NOT_PROVIDED) > 스냅샷 없음(NOT_STARTED) > CLOSED(CLOSED_ESTIMATE)
  // > LIVE
  private ProjectionStatus resolveProjectionStatus(
      CrewStatus crewStatus, Settlement settlement, CrewDashboardSnapshotRow latest) {
    if (settlement != null && settlement.getStatus() == SettlementStatus.SUCCEEDED) {
      return ProjectionStatus.SETTLEMENT_SUCCEEDED;
    }
    if (crewStatus == CrewStatus.CANCELLED) {
      return ProjectionStatus.NOT_PROVIDED;
    }
    if (latest == null) {
      return ProjectionStatus.NOT_STARTED;
    }
    if (crewStatus == CrewStatus.CLOSED) {
      return ProjectionStatus.CLOSED_ESTIMATE;
    }
    return ProjectionStatus.LIVE;
  }

  // 상태별 안내 식별자. LIVE/CLOSED_ESTIMATE인데 내 행이 없으면 입력 부족 안내
  private ProjectionNotice resolveNotice(
      ProjectionStatus status, CrewDashboardParticipantRow myLatestRow) {
    return switch (status) {
      case SETTLEMENT_SUCCEEDED -> ProjectionNotice.SETTLEMENT_RESULT_AVAILABLE;
      case NOT_PROVIDED -> ProjectionNotice.NOT_PROVIDED;
      case NOT_STARTED -> ProjectionNotice.NOT_STARTED;
      case LIVE, CLOSED_ESTIMATE ->
          myLatestRow == null
              ? ProjectionNotice.INSUFFICIENT_PROJECTION_INPUT
              : ProjectionNotice.ESTIMATED_NOT_FINAL;
    };
  }

  // NOT_STARTED는 0, 계산 가능하고 내 행 있으면 값, 그 외 null
  private Integer resolveMySuccessCount(
      ProjectionStatus status, boolean computable, CrewDashboardParticipantRow myLatestRow) {
    if (status == ProjectionStatus.NOT_STARTED) {
      return 0;
    }
    return computable && myLatestRow != null ? myLatestRow.successCount() : null;
  }

  // NOT_STARTED 로스터를 share_ratio null로 매핑 (id asc 정렬은 쿼리에서 보장)
  private List<CrewDashboardParticipantResponse> toRosterParticipants(
      List<CrewParticipantRosterRow> roster, long myParticipantId) {
    return roster.stream()
        .map(
            r ->
                new CrewDashboardParticipantResponse(
                    r.crewParticipantId(),
                    r.nickname(),
                    null,
                    r.crewParticipantId() == myParticipantId))
        .toList();
  }

  // 참여자 목록을 순위(share_ratio desc, id asc) 순으로 매핑, is_me 표시
  private List<CrewDashboardParticipantResponse> toParticipants(
      List<CrewDashboardParticipantRow> rows, long myParticipantId) {
    return sortForRank(rows).stream()
        .map(
            r ->
                new CrewDashboardParticipantResponse(
                    r.crewParticipantId(),
                    r.nickname(),
                    formatShareRatio(r.shareRatio()),
                    r.crewParticipantId() == myParticipantId))
        .toList();
  }

  // 정렬 기준 순위에서 내 참여자의 등수(1-base), 없으면 null
  private Integer rankOf(List<CrewDashboardParticipantRow> rows, long myParticipantId) {
    List<CrewDashboardParticipantRow> ranked = sortForRank(rows);
    for (int i = 0; i < ranked.size(); i++) {
      if (ranked.get(i).crewParticipantId() == myParticipantId) {
        return i + 1;
      }
    }
    return null;
  }

  // 순위 정렬: share_ratio 내림차순, 동률이면 crew_participant_id 오름차순
  private List<CrewDashboardParticipantRow> sortForRank(List<CrewDashboardParticipantRow> rows) {
    return rows.stream()
        .sorted(
            Comparator.comparing(CrewDashboardParticipantRow::shareRatio, Comparator.reverseOrder())
                .thenComparing(CrewDashboardParticipantRow::crewParticipantId))
        .toList();
  }

  private CrewDashboardParticipantRow findRow(
      List<CrewDashboardParticipantRow> rows, long myParticipantId) {
    return rows.stream()
        .filter(r -> r.crewParticipantId() == myParticipantId)
        .findFirst()
        .orElse(null);
  }

  // 종료일까지 남은 일수. 당일 0, 종료일 지났으면 null
  private Integer daysUntilEnd(Crew crew, LocalDateTime now) {
    long days = ChronoUnit.DAYS.between(now.toLocalDate(), crew.getEndAt().toLocalDate());
    return days < 0 ? null : (int) days;
  }

  // RECRUITING/ACTIVE만 산출. 미션 기간 내 다음 스케줄일의 autoCertificationAt
  private OffsetDateTime resolveNextSettlementAt(Crew crew, MissionRule rule, LocalDateTime now) {
    CrewStatus status = crew.getStatus();
    if (status != CrewStatus.RECRUITING && status != CrewStatus.ACTIVE) {
      return null;
    }
    DailySettlementType type = rule.getDailySettlementType();
    Set<DayOfWeek> scheduleDays = resolveScheduleDays(rule);
    LocalDate endDate = crew.getEndAt().toLocalDate();
    LocalDate cursor = maxDate(now.toLocalDate(), crew.getStartAt().toLocalDate());
    for (LocalDate date = cursor; !date.isAfter(endDate); date = date.plusDays(1)) {
      if (!scheduleDays.isEmpty() && !scheduleDays.contains(date.getDayOfWeek())) {
        continue;
      }
      LocalDateTime at = type.autoCertificationAt(date);
      if (at.isAfter(now)) {
        return SeoulDateTimeUtils.toSeoulOffset(at);
      }
    }
    return null;
  }

  // DAILY는 빈 set(매일 매칭), SPECIFIC_DAYS는 스케줄 요일 set
  private Set<DayOfWeek> resolveScheduleDays(MissionRule rule) {
    if (rule.getFrequencyType() != MissionFrequencyType.SPECIFIC_DAYS) {
      return Set.of();
    }
    return crewQueryRepository
        .findScheduleDaysByRuleIds(List.of(rule.getId()))
        .getOrDefault(rule.getId(), List.of())
        .stream()
        .map(DayOfWeek::valueOf)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static LocalDate maxDate(LocalDate a, LocalDate b) {
    return a.isAfter(b) ? a : b;
  }

  private String formatShareRatio(BigDecimal shareRatio) {
    return shareRatio == null ? null : shareRatio.stripTrailingZeros().toPlainString();
  }
}
