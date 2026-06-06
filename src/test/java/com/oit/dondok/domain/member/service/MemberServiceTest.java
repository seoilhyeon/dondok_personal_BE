package com.oit.dondok.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.exception.MemberErrorCode;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.domain.point.entity.PointAccount;
import com.oit.dondok.domain.point.repository.PointAccountRepository;
import com.oit.dondok.global.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

  @Mock private MemberRepository memberRepository;
  @Mock private PointAccountRepository pointAccountRepository;
  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private MemberService memberService;

  @Test
  void signupCreatesMemberAndPointAccount() {
    String email = " User@Example.Com ";
    String password = "password1234";
    String nickname = "돈독러";
    String encodedPassword = "hashed-password";

    given(passwordEncoder.encode(password)).willReturn(encodedPassword);
    given(memberRepository.existsByEmail("user@example.com")).willReturn(false);
    given(memberRepository.existsByNickname(nickname)).willReturn(false);
    given(memberRepository.saveAndFlush(any(Member.class)))
        .willAnswer(
            invocation -> {
              Member member = invocation.getArgument(0);
              return member;
            });

    Member savedMember = memberService.signup(email, password, nickname);

    assertThat(savedMember.getEmail()).isEqualTo("user@example.com");
    assertThat(savedMember.getPasswordHash()).isEqualTo(encodedPassword);
    assertThat(savedMember.getNickname()).isEqualTo(nickname);

    ArgumentCaptor<PointAccount> accountCaptor = ArgumentCaptor.forClass(PointAccount.class);
    then(pointAccountRepository).should().save(accountCaptor.capture());

    PointAccount account = accountCaptor.getValue();
    assertThat(account.getMember()).isSameAs(savedMember);
    assertThat(account.getAvailableBalance()).isZero();
    assertThat(account.getReservedBalance()).isZero();
    assertThat(account.getLockedBalance()).isZero();

    then(memberRepository).should().saveAndFlush(savedMember);
  }

  @Test
  void signupThrowsWhenEmailAlreadyExistsAndSkipsPointAccountCreation() {
    String email = "user@example.com";
    given(memberRepository.existsByEmail(email)).willReturn(true);

    assertThatThrownBy(() -> memberService.signup(email, "password1234", "돈독러"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MemberErrorCode.EMAIL_ALREADY_EXISTS);

    then(memberRepository).should(never()).existsByNickname(anyString());
    then(pointAccountRepository).shouldHaveNoInteractions();
  }

  @Test
  void signupThrowsWhenNicknameAlreadyExistsAndSkipsPointAccountCreation() {
    String email = "user@example.com";
    given(memberRepository.existsByEmail(email)).willReturn(false);
    given(memberRepository.existsByNickname("돈독러")).willReturn(true);

    assertThatThrownBy(() -> memberService.signup(email, "password1234", "돈독러"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MemberErrorCode.NICKNAME_ALREADY_EXISTS);

    then(pointAccountRepository).shouldHaveNoInteractions();
    then(memberRepository).should(never()).saveAndFlush(any(Member.class));
  }
}
