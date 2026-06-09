package com.oit.dondok.infra.image.adapter;

import com.oit.dondok.domain.image.entity.ImageReEncodeTask;
import com.oit.dondok.domain.mission.port.ImageProcessingPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 이미 claim(lease)된 작업을 처리한다. reEncode(S3 왕복)는 트랜잭션/락 밖에서 수행하고,
// 결과만 ReEncodeTaskResultWriter의 짧은 트랜잭션으로 기록한다 → DB 커넥션/락을 S3 동안 점유하지 않는다.
@Component
@RequiredArgsConstructor
public class ReEncodeTaskProcessor {

  private final ImageProcessingPort imageProcessingPort;
  private final ReEncodeTaskResultWriter resultWriter;

  public void process(ImageReEncodeTask task) {
    // claim 시 발급된 fencing token. 결과 기록은 이 version이 여전히 소유 중일 때만 반영된다(stale write 방어).
    long attemptVersion = task.getAttemptVersion();
    try {
      imageProcessingPort.reEncode(task.getS3Key()); // 락/트랜잭션 밖
    } catch (RuntimeException e) {
      resultWriter.fail(task.getId(), attemptVersion, e);
      return;
    }
    // reEncode 성공 기록은 catch 밖에 둬, complete 실패가 fail 기록으로 잘못 이어지지 않게 한다.
    resultWriter.complete(task.getId(), attemptVersion);
  }
}
