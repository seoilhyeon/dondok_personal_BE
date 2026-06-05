package com.oit.dondok.domain.image.port;

import java.util.UUID;

public interface ImageObjectKeyPolicy {

  ImageObjectKey profileImageKey(UUID memberUuid, UUID fileId);

  ImageObjectKey crewImageKey(UUID memberUuid, UUID fileId);

  ImageObjectKey missionImageKey(Long crewId, Long crewParticipantId, UUID fileId);

  boolean matchesProfileKey(UUID memberUuid, String key);

  boolean matchesCrewKey(UUID memberUuid, String key);

  boolean matchesMissionKey(Long crewId, Long crewParticipantId, String key);
}
