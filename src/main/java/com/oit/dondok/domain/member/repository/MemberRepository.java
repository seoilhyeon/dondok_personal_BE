package com.oit.dondok.domain.member.repository;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.entity.OAuthProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

  Optional<Member> findByUuid(UUID uuid);

  Optional<Member> findByEmail(String email);

  Optional<Member> findByOauthProviderAndOauthProviderId(
      OAuthProvider oauthProvider, String oauthProviderId);

  boolean existsByEmail(String email);

  boolean existsByNickname(String nickname);

  boolean existsByNicknameAndUuidNot(String nickname, UUID uuid);
}
