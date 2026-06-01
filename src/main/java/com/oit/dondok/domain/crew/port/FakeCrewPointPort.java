package com.oit.dondok.domain.crew.port;

import com.oit.dondok.domain.crew.entity.CrewParticipant;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class FakeCrewPointPort implements CrewPointPort {

  // TODO: 실제 포인트 락 로직 구현 필요 (PointAccount 잔액 차감 + PointHistory append)
  @Override
  public void lockForHostParticipant(CrewParticipant crewParticipant) {}
}
