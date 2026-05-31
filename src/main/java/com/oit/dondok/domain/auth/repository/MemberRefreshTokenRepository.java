package com.oit.dondok.domain.auth.repository;

import com.oit.dondok.domain.auth.entity.MemberRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRefreshTokenRepository extends JpaRepository<MemberRefreshToken, Long> {}
