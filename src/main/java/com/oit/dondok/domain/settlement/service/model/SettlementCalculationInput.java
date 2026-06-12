package com.oit.dondok.domain.settlement.service.model;

import com.oit.dondok.domain.settlement.entity.RemainderPolicy;
import java.util.List;

public record SettlementCalculationInput(
    RemainderPolicy remainderPolicy, List<SettlementParticipantInput> participants) {}
