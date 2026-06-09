package com.oit.dondok.domain.image.repository;

import com.oit.dondok.domain.image.entity.ImageReEncodeTask;
import org.springframework.data.jpa.repository.JpaRepository;

// 작업 소유권(행 잠금)은 ImageReEncodeTaskClaimRepository의 claim 단계가 담당하고,
// 여기서는 결과 기록(complete/fail)용 단순 조회만 사용한다.
public interface ImageReEncodeTaskRepository extends JpaRepository<ImageReEncodeTask, Long> {}
