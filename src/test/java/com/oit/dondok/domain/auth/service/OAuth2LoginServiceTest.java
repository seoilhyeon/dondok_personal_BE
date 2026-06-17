package com.oit.dondok.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.entity.OAuthProvider;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.domain.point.entity.PointAccount;
import com.oit.dondok.domain.point.repository.PointAccountRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginServiceTest {

  @Mock private MemberRepository memberRepository;
  @Mock private PointAccountRepository pointAccountRepository;
  @Mock private PlatformTransactionManager transactionManager;

  @Test
  void loginCreatesOAuthMemberAndPointAccountWhenMemberDoesNotExist() {
    OAuth2LoginService service = oauth2LoginService();
    OAuthUserInfo userInfo =
        new OAuthUserInfo(
            OAuthProvider.GOOGLE, "google-sub-1", "User@Example.Com", true, "googleUser", null);

    given(
            memberRepository.findByOauthProviderAndOauthProviderId(
                OAuthProvider.GOOGLE, "google-sub-1"))
        .willReturn(Optional.empty());
    given(memberRepository.findByEmail("user@example.com")).willReturn(Optional.empty());
    given(memberRepository.existsByNickname("googleUser")).willReturn(false);
    given(memberRepository.saveAndFlush(any(Member.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(transactionManager.getTransaction(any(TransactionDefinition.class)))
        .willReturn(new SimpleTransactionStatus());

    UUID memberUuid = service.login(userInfo);

    ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
    then(memberRepository).should().saveAndFlush(memberCaptor.capture());
    Member savedMember = memberCaptor.getValue();

    assertThat(memberUuid).isEqualTo(savedMember.getUuid());
    assertThat(savedMember.getEmail()).isEqualTo("user@example.com");
    assertThat(savedMember.getPasswordHash()).isNull();
    assertThat(savedMember.getNickname()).isEqualTo("googleUser");
    assertThat(savedMember.getOauthProvider()).isEqualTo(OAuthProvider.GOOGLE);
    assertThat(savedMember.getOauthProviderId()).isEqualTo("google-sub-1");

    ArgumentCaptor<PointAccount> accountCaptor = ArgumentCaptor.forClass(PointAccount.class);
    then(pointAccountRepository).should().save(accountCaptor.capture());
    assertThat(accountCaptor.getValue().getMember()).isSameAs(savedMember);
  }

  @Test
  void loginReturnsExistingOAuthMemberUuidWithoutCreatingPointAccount() {
    OAuth2LoginService service = oauth2LoginService();
    Member member =
        Member.createOAuthMember(
            "user@example.com", "googleUser", OAuthProvider.GOOGLE, "google-sub-1");
    OAuthUserInfo userInfo =
        new OAuthUserInfo(
            OAuthProvider.GOOGLE, "google-sub-1", "user@example.com", true, "googleUser", null);

    given(
            memberRepository.findByOauthProviderAndOauthProviderId(
                OAuthProvider.GOOGLE, "google-sub-1"))
        .willReturn(Optional.of(member));

    UUID memberUuid = service.login(userInfo);

    assertThat(memberUuid).isEqualTo(member.getUuid());
    then(memberRepository)
        .should()
        .findByOauthProviderAndOauthProviderId(OAuthProvider.GOOGLE, "google-sub-1");
    then(memberRepository).shouldHaveNoMoreInteractions();
    then(pointAccountRepository).shouldHaveNoInteractions();
  }

  private OAuth2LoginService oauth2LoginService() {
    return new OAuth2LoginService(memberRepository, pointAccountRepository, transactionManager);
  }
}
