package com.oit.dondok.domain.settlement.repository;

import static com.oit.dondok.domain.crew.entity.QCrew.crew;
import static com.oit.dondok.domain.crew.entity.QCrewParticipant.crewParticipant;
import static com.oit.dondok.domain.settlement.entity.QSettlement.settlement;

import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.member.entity.QMember;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SettlementQueryRepository {

  private final JPAQueryFactory queryFactory;

  public Optional<Settlement> findAccessibleByIdAndMemberUuid(Long settlementId, UUID memberUuid) {
    QMember hostMember = new QMember("hostMember");

    return Optional.ofNullable(
        queryFactory
            .select(settlement)
            .from(settlement)
            .join(settlement.crew, crew)
            .fetchJoin()
            .join(crew.hostMember, hostMember)
            .fetchJoin()
            .leftJoin(crewParticipant)
            .on(
                crewParticipant
                    .crew
                    .eq(crew)
                    .and(crewParticipant.member.uuid.eq(memberUuid))
                    .and(crewParticipant.status.eq(CrewParticipantStatus.LOCKED)))
            .where(
                settlement
                    .id
                    .eq(settlementId)
                    .and(hostMember.uuid.eq(memberUuid).or(crewParticipant.id.isNotNull())))
            .fetchOne());
  }
}
