package com.oit.dondok.domain.crew.port;

import com.oit.dondok.domain.crew.entity.CrewParticipant;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// TODO: prod 프로파일용 실제 구현체(CrewPointPort) 가 없으면 prod 부팅 시 NoSuchBeanDefinitionException 발생.
//       prod 배포 전 반드시 real implementation 을 등록할 것.
@Component
@Profile({"local", "dev", "integration"})
public class FakeCrewPointPort implements CrewPointPort {

  // TODO: 실제 포인트 락 로직 구현 필요 (PointAccount 잔액 차감 + PointHistory append)
  @Override
  public void lockForHostParticipant(CrewParticipant crewParticipant) {}

  // TODO: 실제 reserve 로직 구현 필요 (available_balance → reserved_balance)
  @Override
  public void reserveForPendingParticipant(CrewParticipant participant) {}

  // TODO: 실제 release 로직 구현 필요 (reserved_balance → available_balance)
  @Override
  public void releasePendingReserve(CrewParticipant participant) {}
}
