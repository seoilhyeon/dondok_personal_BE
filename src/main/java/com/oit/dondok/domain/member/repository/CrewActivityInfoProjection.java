package com.oit.dondok.domain.member.repository;

public record CrewActivityInfoProjection(
    long totalCrewCount, long activeCrewCount, long completedCrewCount) {}
