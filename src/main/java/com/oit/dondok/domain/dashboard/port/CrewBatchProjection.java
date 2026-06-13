package com.oit.dondok.domain.dashboard.port;

// 특정 크루에서 특정 회원의 직전(=최신) 일일 정산 배치 기준 projection 값.

import java.math.BigDecimal;

public record CrewBatchProjection(
    Long crewId,
    BigDecimal shareRatio, // 최신 배치 기준 나의 지분율. 산출 불가 시 null
    Long expectedRefundAmount, // 최신 배치 기준 나의 예상 환급금(원). 산출 불가 시 null
    Long previousExpectedRefundAmount) {} // 최신 배치의 직전 배치 기준 나의 예상 환급금(원). 비교 대상 배치가 없으면 null
