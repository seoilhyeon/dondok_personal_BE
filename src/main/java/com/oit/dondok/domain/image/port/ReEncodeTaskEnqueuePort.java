package com.oit.dondok.domain.image.port;

// 미션 로그 생성 르탠잭션에서 reEncode 작업을 outbox에 적재하는 포트
public interface ReEncodeTaskEnqueuePort {
  void enqueue(Long missionLogId, String s3Key);
}
