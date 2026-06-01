package com.oit.dondok.domain.crew.port;

import com.oit.dondok.domain.crew.entity.CrewParticipant;

public interface CrewPointPort {

  void lockForHostParticipant(CrewParticipant crewParticipant);
}
