package com.oit.dondok.infra.image.adapter;

import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.image.port.ImageObjectKeyPolicy;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DefaultImageObjectKeyPolicy implements ImageObjectKeyPolicy {

  @Override
  public ImageObjectKey profileImageKey(UUID memberUuid, UUID fileId) {
    return new ImageObjectKey("profile/%s/%s".formatted(memberUuid, fileId));
  }

  @Override
  public ImageObjectKey crewImageKey(UUID memberUuid, UUID fileId) {
    return new ImageObjectKey("crew/%s/%s".formatted(memberUuid, fileId));
  }

  @Override
  public ImageObjectKey missionImageKey(Long crewId, Long crewParticipantId, UUID fileId) {
    return new ImageObjectKey("mission/%d/%d/%s".formatted(crewId, crewParticipantId, fileId));
  }

  @Override
  public boolean matchesProfileKey(UUID memberUuid, String key) {
    return matchesScopedKey(key, "profile", memberUuid.toString());
  }

  @Override
  public boolean matchesCrewKey(UUID memberUuid, String key) {
    return matchesScopedKey(key, "crew", memberUuid.toString());
  }

  // participant 소유 인가는 key로 판정 불가하므로
  // mission 도메인(getOwnedParticipant)에서 별도 수행한다.
  @Override
  public boolean matchesMissionKey(Long crewId, Long crewParticipantId, String key) {
    if (crewId == null || crewParticipantId == null) {
      return false;
    }
    return matchesScopedKey(key, "mission", crewId.toString(), crewParticipantId.toString());
  }

  // "{prefixSegments...}/{fileUuid}" 형식 검증: 앞쪽 고정(prefix) 세그먼트가 모두 일치하고 마지막이 UUID인지 확인한다.
  private boolean matchesScopedKey(String key, String... prefixSegments) {
    if (key == null) {
      return false;
    }
    String[] parts = key.split("/", -1);
    if (parts.length != prefixSegments.length + 1) {
      return false;
    }
    for (int i = 0; i < prefixSegments.length; i++) {
      if (!prefixSegments[i].equals(parts[i])) {
        return false;
      }
    }

    return isUuid(parts[parts.length - 1]);
  }

  private boolean isUuid(String value) {
    try {
      UUID.fromString(value);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
