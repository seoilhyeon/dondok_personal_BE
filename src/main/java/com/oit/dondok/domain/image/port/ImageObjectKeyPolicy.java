package com.oit.dondok.domain.image.port;

import java.util.UUID;

public interface ImageObjectKeyPolicy {

  ImageObjectKey missionImageKey(Long crewId, Long crewParticipantId, UUID fileId);

  ImageObjectKey profileImageKey(UUID memberUuid, UUID fileId);

  ImageObjectKey crewImageKey(UUID memberUuid, UUID fileId);
}
