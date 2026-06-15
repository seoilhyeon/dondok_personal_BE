package com.oit.dondok.domain.mission.repository;

import java.time.LocalDateTime;
import java.util.UUID;

// 피드 reaction batch 조회용
public record ReactionRow(Long missionLogId, String reactionType, UUID memberUuid, LocalDateTime createdAt) {}
