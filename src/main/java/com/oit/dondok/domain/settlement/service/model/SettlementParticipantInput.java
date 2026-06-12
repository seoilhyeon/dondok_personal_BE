package com.oit.dondok.domain.settlement.service.model;

public record SettlementParticipantInput(
    String participantKey,
    boolean host,
    long depositAmount,
    int successCountRaw,
    int recognizedSuccessCount,
    int recognizedDatesCount,
    int excludedSuccessCount) {}
