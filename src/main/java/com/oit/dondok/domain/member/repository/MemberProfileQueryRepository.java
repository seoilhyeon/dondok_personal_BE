package com.oit.dondok.domain.member.repository;

import static com.oit.dondok.domain.crew.entity.QCrew.crew;
import static com.oit.dondok.domain.member.entity.QMember.member;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class MemberProfileQueryRepository {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

  private final JPAQueryFactory queryFactory;

  @Transactional(readOnly = true)
  public Optional<MemberProfileProjection> findByMemberUuid(UUID memberUuid) {
    NumberExpression<Long> hostedCrewCount = crew.id.count();

    Tuple profile =
        queryFactory
            .select(
                member.uuid,
                member.email,
                member.nickname,
                member.profileImageS3Key,
                member.statusMessage,
                hostedCrewCount,
                member.status,
                member.createdAt)
            .from(member)
            .leftJoin(crew)
            .on(crew.hostMember.eq(member))
            .where(member.uuid.eq(memberUuid))
            .groupBy(
                member.id,
                member.uuid,
                member.email,
                member.nickname,
                member.profileImageS3Key,
                member.statusMessage,
                member.status,
                member.createdAt)
            .fetchOne();

    if (profile == null) {
      return Optional.empty();
    }

    return Optional.of(
        new MemberProfileProjection(
            profile.get(member.uuid),
            profile.get(member.email),
            profile.get(member.nickname),
            profile.get(member.profileImageS3Key),
            profile.get(member.statusMessage),
            Optional.ofNullable(profile.get(hostedCrewCount)).orElse(0L),
            profile.get(member.status),
            toSeoulOffset(profile.get(member.createdAt))));
  }

  private static OffsetDateTime toSeoulOffset(LocalDateTime createdAt) {
    if (createdAt == null) {
      return null;
    }

    return createdAt.atZone(SEOUL_ZONE).toOffsetDateTime();
  }
}
