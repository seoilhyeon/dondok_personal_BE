package com.oit.dondok.domain.image.repository;

import static com.oit.dondok.domain.image.entity.QImageReEncodeTask.imageReEncodeTask;

import com.oit.dondok.domain.image.entity.ImageReEncodeTask;
import com.oit.dondok.domain.image.entity.ReEncodeTaskStatus;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

// 재처리 배치의 claim 단계. PENDING이고 기한이 도래한 작업을 FOR UPDATE SKIP LOCKED로 선점하고,
// next_attempt_at을 lease만큼 미뤄 락 해제 이후에도 다른 워커가 재선점하지 못하게 한다.
// 짧은 트랜잭션에서 claim만 수행하고, 실제 reEncode(S3 왕복)는 락 밖 별도 트랜잭션에서 처리한다.
@Repository
@RequiredArgsConstructor
public class ImageReEncodeTaskClaimRepository {

  // Hibernate SKIP LOCKED 힌트 (LockOptions.SKIP_LOCKED).
  private static final int SKIP_LOCKED = -2;

  private final JPAQueryFactory queryFactory;

  @Transactional
  public List<ImageReEncodeTask> claimPendingTasks(
      LocalDateTime now, LocalDateTime leaseUntil, int limit) {
    List<ImageReEncodeTask> claimed =
        queryFactory
            .selectFrom(imageReEncodeTask)
            .where(
                imageReEncodeTask.status.eq(ReEncodeTaskStatus.PENDING),
                imageReEncodeTask.nextAttemptAt.loe(now))
            .orderBy(imageReEncodeTask.nextAttemptAt.asc())
            .limit(limit)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .setHint("jakarta.persistence.lock.timeout", SKIP_LOCKED)
            .fetch();
    // 선점한 작업의 다음 시도 시각을 미뤄(lease) 다른 워커/다음 tick의 재선점을 차단한다.
    claimed.forEach(task -> task.lease(leaseUntil));
    return claimed;
  }
}
