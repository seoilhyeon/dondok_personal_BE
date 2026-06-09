package com.oit.dondok.infra.image.event;

// reEncode 작업 적재 이벤트. AFTER_COMMIT 시점에 즉시 시도 경로를 트리거한다.
public record ReEncodeTaskCreatedEvent(Long taskId) {}
