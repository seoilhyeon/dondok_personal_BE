package com.oit.dondok.domain.notification.repository;

import static com.oit.dondok.domain.notification.entity.QNotification.notification;

import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class NotificationQueryRepository {

  private final JPAQueryFactory queryFactory;

  public NotificationQueryRepository(JPAQueryFactory queryFactory) {
    this.queryFactory = queryFactory;
  }

  @Transactional(readOnly = true)
  public List<NotificationProjection> findByCursor(
      UUID memberUuid, int limit, LocalDateTime cursorOccurredAt, Long cursorId) {
    return queryFactory
        .select(
            Projections.constructor(
                NotificationProjection.class,
                notification.id,
                notification.uuid,
                notification.eventType,
                notification.resourceType,
                notification.resourceId,
                notification.deepLink,
                notification.displayText,
                notification.crewName,
                notification.requiresRefetch,
                notification.occurredAt,
                notification.readAt))
        .from(notification)
        .where(
            notification.member.uuid.eq(memberUuid),
            buildCursorCondition(cursorOccurredAt, cursorId))
        .orderBy(notification.occurredAt.desc(), notification.id.desc())
        .limit(limit)
        .fetch();
  }

  private Predicate buildCursorCondition(LocalDateTime cursorOccurredAt, Long cursorId) {
    if (cursorOccurredAt == null || cursorId == null) {
      return null;
    }
    return notification
        .occurredAt
        .lt(cursorOccurredAt)
        .or(notification.occurredAt.eq(cursorOccurredAt).and(notification.id.lt(cursorId)));
  }
}
