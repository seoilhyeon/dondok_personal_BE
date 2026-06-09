package com.oit.dondok.domain.image.repository;

import static com.oit.dondok.domain.image.entity.QImageReEncodeTask.imageReEncodeTask;

import com.oit.dondok.domain.image.entity.ImageReEncodeTask;
import com.oit.dondok.domain.image.entity.ReEncodeTaskStatus;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

// reEncode 작업의 claim 단계. PENDING이고 기한이 도래한 작업을 행 잠금으로 선점하고
// next_attempt_at을 lease만큼 미뤄 락 해제 이후에도 다른 워커/리스너가 재선점하지 못하게 한다.
// 이 짧은 트랜잭션에서 소유권만 잡고, 실제 reEncode(S3 왕복)는 락/트랜잭션 밖에서 수행한다.
@Repository
@RequiredArgsConstructor
public class ImageReEncodeTaskClaimRepository {

  // Hibernate SKIP LOCKED 힌트 (LockOptions.SKIP_LOCKED).
  private static final int SKIP_LOCKED = -2;
  // 선점 후 reEncode 처리가 끝날 때까지 다른 워커의 재선점을 막는 시간. 워커가 죽으면 이 시간 후 회수된다.
  private static final Duration LEASE = Duration.ofMinutes(5);

  private final JPAQueryFactory queryFactory;

  // 배치용: 기한 도래 PENDING 작업을 SKIP LOCKED로 겹침 없이 다건 선점한다.
  @Transactional
  public List<ImageReEncodeTask> claimPendingTasks(LocalDateTime now, int limit) {
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
    LocalDateTime leaseUntil = now.plus(LEASE);
    claimed.forEach(task -> task.claim(leaseUntil));
    return claimed;
  }

  // fast-path용: 특정 작업을 단건 선점한다. 이미 처리됐거나 다른 쪽이 선점(lease)했으면 empty.
  // SKIP LOCKED로, 다른 워커가 이미 잡고 있는 row면 대기하지 않고 즉시 건너뛴다(best-effort, 배치가 처리).
  @Transactional
  public Optional<ImageReEncodeTask> claimById(Long taskId, LocalDateTime now) {
    ImageReEncodeTask task =
        queryFactory
            .selectFrom(imageReEncodeTask)
            .where(
                imageReEncodeTask.id.eq(taskId),
                imageReEncodeTask.status.eq(ReEncodeTaskStatus.PENDING),
                imageReEncodeTask.nextAttemptAt.loe(now))
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .setHint("jakarta.persistence.lock.timeout", SKIP_LOCKED)
            .fetchFirst();
    if (task == null) {
      return Optional.empty();
    }
    task.claim(now.plus(LEASE));
    return Optional.of(task);
  }
}
