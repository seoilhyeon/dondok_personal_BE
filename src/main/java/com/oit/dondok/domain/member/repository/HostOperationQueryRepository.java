package com.oit.dondok.domain.member.repository;

import static com.oit.dondok.domain.crew.entity.QCrew.crew;
import static com.oit.dondok.domain.member.entity.QMember.member;
import static com.oit.dondok.domain.mission.entity.QMissionLog.missionLog;

import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.QCrewParticipant;
import com.oit.dondok.domain.mission.entity.CertificationStatus;
import com.querydsl.jpa.impl.JPAQueryFactory;
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
}
