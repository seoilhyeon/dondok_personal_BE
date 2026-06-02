package com.oit.dondok.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemberTest {

  @Test
  void updateProfileUpdatesNicknameProfileImageS3KeyAndStatusMessage() {
    Member member = Member.create("member@example.com", "encoded-password", "기존닉네임");

    member.updateProfile("새닉네임", "profile/new-image.png", "새 상태 메시지");

    assertThat(member.getNickname()).isEqualTo("새닉네임");
    assertThat(member.getProfileImageS3Key()).isEqualTo("profile/new-image.png");
    assertThat(member.getStatusMessage()).isEqualTo("새 상태 메시지");
  }

  @Test
  void updateProfileAllowsNullProfileImageS3KeyAndStatusMessageForRemoval() {
    Member member = Member.create("member@example.com", "encoded-password", "기존닉네임");
    member.updateProfile("기존닉네임", "profile/old-image.png", "기존 상태 메시지");

    member.updateProfile("기존닉네임", null, null);

    assertThat(member.getNickname()).isEqualTo("기존닉네임");
    assertThat(member.getProfileImageS3Key()).isNull();
    assertThat(member.getStatusMessage()).isNull();
  }
}
