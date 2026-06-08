package com.oit.dondok.domain.image.repository;

import com.oit.dondok.domain.image.entity.ImageReEncodeTask;
import com.oit.dondok.domain.image.entity.ReEncodeTaskStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.awt.print.Pageable;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

public interface ImageReEncodeTaskRepository extends JpaRepository<ImageReEncodeTask, Long> {
  // 재처리 배치용 claim 조회. PESSIMISTIC_WRITE + SKIP LOCKED(timeout -2)로
  // 즉시 시도 경로/다른 배치 인스턴스가 잡은 row는 건너뛰어 중복 처리를 막는다.
  // FAILED는 status 필터로 자연히 제외되므로 retry_count 조건은 두지 않는다.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
  List<ImageReEncodeTask> findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAt(
      ReEncodeTaskStatus status, LocalDateTime threshold, Pageable pageable);
}
