package com.oit.dondok.domain.member.repository;

import static com.oit.dondok.domain.crew.entity.QCrew.crew;
import static com.oit.dondok.domain.member.entity.QMember.member;
import static com.oit.dondok.domain.mission.entity.QMissionLog.missionLog;

import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.entity.QCrewParticipant;
import com.oit.dondok.domain.mission.entity.CertificationStatus;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class HostOperationQueryRepository {

  private final JPAQueryFactory queryFactory;

  public boolean existsByMemberUuid(UUID memberUuid) {
    return queryFactory.selectOne().from(member).where(member.uuid.eq(memberUuid)).fetchFirst()
        != null;
  }

  public long countTotalPendingOperationsByHost(UUID memberUuid) {
    return countPendingReviewsByHost(memberUuid) + countPendingApplicationsByHost(memberUuid);
  }

  private long countPendingReviewsByHost(UUID memberUuid) {
    QCrewParticipant participant = new QCrewParticipant("hostPendingReviewParticipant");

    return zeroIfNull(
        queryFactory
            .select(missionLog.id.count())
            .from(missionLog)
            .join(missionLog.crewParticipant, participant)
            .join(participant.crew, crew)
            .where(
                crew.hostMember.uuid.eq(memberUuid),
                missionLog.certificationStatus.eq(CertificationStatus.PENDING_REVIEW))
            .fetchOne());
  }

  private long countPendingApplicationsByHost(UUID memberUuid) {
    QCrewParticipant participant = new QCrewParticipant("hostPendingApplicationParticipant");

    return zeroIfNull(
        queryFactory
            .select(participant.id.count())
            .from(participant)
            .join(participant.crew, crew)
            .where(
                crew.hostMember.uuid.eq(memberUuid),
                participant.status.eq(CrewParticipantStatus.PENDING))
            .fetchOne());
  }

  private static long zeroIfNull(Long value) {
    return value == null ? 0L : value;
  }

  // 운영 콘솔 진입 대상 기본 방장 크루
  // 방장 크루 중 대기 건수(검증 대기 + 가입 신청 대기) 합이 가장 많은 크루,
  // 동률이면 가장 최근 생성 크루. 방장 크루가 없으면 empty.
  public Optional<Long> findDefaultHostCrewId(UUID memberUuid) {
    List<Tuple> hostCrews =
        queryFactory
            .select(crew.id, crew.createdAt)
            .from(crew)
            .where(crew.hostMember.uuid.eq(memberUuid), crew.status.ne(CrewStatus.CANCELLED))
            .fetch();
    if (hostCrews.isEmpty()) {
      return Optional.empty();
    }

    Map<Long, Long> pendingReviewByCrew = countPendingReviewsByCrew(memberUuid);
    Map<Long, Long> pendingApplicationByCrew = countPendingApplicationsByCrew(memberUuid);

    return hostCrews.stream()
        .max(
            Comparator.comparingLong(
                    (Tuple t) ->
                        pendingCountOf(
                            pendingReviewByCrew, pendingApplicationByCrew, t.get(crew.id)))
                .thenComparing(t -> t.get(crew.createdAt)))
        .map(t -> t.get(crew.id));
  }

  private long pendingCountOf(
      Map<Long, Long> reviewByCrew, Map<Long, Long> applicationByCrew, Long crewId) {
    return reviewByCrew.getOrDefault(crewId, 0L) + applicationByCrew.getOrDefault(crewId, 0L);
  }

  private Map<Long, Long> countPendingReviewsByCrew(UUID memberUuid) {
    QCrewParticipant participant = new QCrewParticipant("hostPendingReviewByCrewParticipant");

    return toCountMap(
        queryFactory
            .select(crew.id, missionLog.id.count())
            .from(missionLog)
            .join(missionLog.crewParticipant, participant)
            .join(participant.crew, crew)
            .where(
                crew.hostMember.uuid.eq(memberUuid),
                crew.status.ne(CrewStatus.CANCELLED),
                missionLog.certificationStatus.eq(CertificationStatus.PENDING_REVIEW))
            .groupBy(crew.id)
            .fetch());
  }

  private Map<Long, Long> countPendingApplicationsByCrew(UUID memberUuid) {
    QCrewParticipant participant = new QCrewParticipant("hostPendingApplicationByCrewParticipant");

    return toCountMap(
        queryFactory
            .select(crew.id, participant.id.count())
            .from(participant)
            .join(participant.crew, crew)
            .where(
                crew.hostMember.uuid.eq(memberUuid),
                crew.status.ne(CrewStatus.CANCELLED),
                participant.status.eq(CrewParticipantStatus.PENDING))
            .groupBy(crew.id)
            .fetch());
  }

  private Map<Long, Long> toCountMap(List<Tuple> rows) {
    Map<Long, Long> countByCrewId = new HashMap<>();
    for (Tuple row : rows) {
      countByCrewId.put(row.get(0, Long.class), row.get(1, Long.class));
    }
    return countByCrewId;
  }
}
