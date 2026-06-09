package com.oit.dondok.domain.mission.service;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.domain.mission.exception.MissionErrorCode;
import com.oit.dondok.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SystemMemberProvider {

  private static final String SYSTEM_MEMBER_EMAIL = "system@dondok.internal";

  private final MemberRepository memberRepository;

  // 자동 처리 이력의 actor로 사용할 내부 시스템 계정 ID를 캐싱한다.
  @Cacheable("systemMemberId")
  @Transactional(readOnly = true)
  public Long getSystemMemberId() {
    return memberRepository
        .findByEmail(SYSTEM_MEMBER_EMAIL)
        .map(Member::getId)
        .orElseThrow(() -> new CustomException(MissionErrorCode.SYSTEM_MEMBER_NOT_FOUND));
  }
}
