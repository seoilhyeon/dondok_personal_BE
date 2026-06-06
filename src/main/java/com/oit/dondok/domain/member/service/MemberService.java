package com.oit.dondok.domain.member.service;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.exception.MemberErrorCode;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.domain.point.entity.PointAccount;
import com.oit.dondok.domain.point.repository.PointAccountRepository;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

  private final MemberRepository memberRepository;
  private final PointAccountRepository pointAccountRepository;
  private final PasswordEncoder passwordEncoder;

  private static final int MIN_NICKNAME_LENGTH = 2;
  private static final int MAX_NICKNAME_LENGTH = 10;

  @Transactional
  public Member signup(String email, String password, String nickname) {
    email = email.trim().toLowerCase(Locale.ROOT);
    nickname = nickname.trim();

    if (nickname.length() < MIN_NICKNAME_LENGTH || nickname.length() > MAX_NICKNAME_LENGTH) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }

    if (memberRepository.existsByEmail(email)) {
      throw new CustomException(MemberErrorCode.EMAIL_ALREADY_EXISTS);
    }

    if (memberRepository.existsByNickname(nickname)) {
      throw new CustomException(MemberErrorCode.NICKNAME_ALREADY_EXISTS);
    }

    String passwordHash = passwordEncoder.encode(password);

    Member member = Member.create(email, passwordHash, nickname);

    try {
      Member savedMember = memberRepository.saveAndFlush(member);
      pointAccountRepository.save(PointAccount.create(savedMember));
      return savedMember;
    } catch (DataIntegrityViolationException exception) {
      Throwable cause = exception;

      while (cause != null) {
        if (cause instanceof ConstraintViolationException constraintException) {
          String constraintName = constraintException.getConstraintName();

          if ("uk_member_email".equalsIgnoreCase(constraintName)) {
            throw new CustomException(MemberErrorCode.EMAIL_ALREADY_EXISTS, exception);
          }

          if ("uk_member_nickname".equalsIgnoreCase(constraintName)) {
            throw new CustomException(MemberErrorCode.NICKNAME_ALREADY_EXISTS, exception);
          }

          break;
        }

        cause = cause.getCause();
      }

      throw exception;
    }
  }
}
