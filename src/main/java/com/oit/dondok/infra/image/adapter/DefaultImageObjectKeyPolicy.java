package com.oit.dondok.infra.image.adapter;

import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.image.port.ImageObjectKeyPolicy;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DefaultImageObjectKeyPolicy implements ImageObjectKeyPolicy {

  @Override
  public ImageObjectKey missionImageKey(Long crewId, Long crewParticipantId, UUID fileId) {
    return new ImageObjectKey("mission/%d/%d/%s".formatted(crewId, crewParticipantId, fileId));
  }

  @Override
  public ImageObjectKey profileImageKey(UUID memberUuid, UUID fileId) {
    return new ImageObjectKey("profile/%s/%s".formatted(memberUuid, fileId));
  }

  @Override
  public ImageObjectKey crewImageKey(UUID memberUuid, UUID fileId) {
    return new ImageObjectKey("crew/%s/%s".formatted(memberUuid, fileId));
  }
}
