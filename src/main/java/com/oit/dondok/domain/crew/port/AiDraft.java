package com.oit.dondok.domain.crew.port;

import java.util.List;

public record AiDraft(
    String title,
    String description,
    String frequencyType,
    List<String> missionScheduleDays,
    String dailySettlementType,
    long depositAmount,
    int durationDays) {}
