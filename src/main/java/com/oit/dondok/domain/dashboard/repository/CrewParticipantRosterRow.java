package com.oit.dondok.domain.dashboard.repository;

// NOT_STARTED 대시보드에서 스냅샷 없이 크루원 목록을 채우기 위한 경량 행 (id, 닉네임)
public record CrewParticipantRosterRow(
    Long crewParticipantId, String nickname
) {
}
