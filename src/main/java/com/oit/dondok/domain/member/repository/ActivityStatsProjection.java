package com.oit.dondok.domain.member.repository;

import java.math.BigDecimal;

public record ActivityStatsProjection(
    long totalRecognizedSuccessCount,
    BigDecimal highestShareRatio,
    Long highestShareRatioCrewId,
    String highestShareRatioCrewTitle,
    String averageSuccessRate) {}
