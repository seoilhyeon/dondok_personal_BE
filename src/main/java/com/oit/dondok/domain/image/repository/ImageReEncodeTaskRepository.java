package com.oit.dondok.domain.image.repository;

import com.oit.dondok.domain.image.entity.ImageReEncodeTask;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ImageReEncodeTaskRepository extends JpaRepository<ImageReEncodeTask, Long> {

  // processor가 load + 상태 전이를 원자적으로 처리하도록 행을 PESSIMISTIC_WRITE로 잠근다.
  // 즉시 시도(listener)와 배치가 같은 작업을 잡아도, 한쪽이 잠금 획득 후 DONE/FAILED로 바꾸면
  // 다른 쪽은 status guard에 걸려 skip → 중복 reEncode 방지.
  // 배치의 다중 워커 간 중복 선점은 ImageReEncodeTaskClaimRepository(SKIP LOCKED + lease)가 차단한다.
  @Override
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<ImageReEncodeTask> findById(Long id);
}
