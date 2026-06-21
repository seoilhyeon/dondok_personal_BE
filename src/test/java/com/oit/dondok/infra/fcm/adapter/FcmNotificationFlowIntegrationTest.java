package com.oit.dondok.infra.fcm.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.oit.dondok.IntegrationTest;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.notification.entity.NotificationDevice;
import com.oit.dondok.domain.notification.entity.NotificationPlatform;
import com.oit.dondok.domain.notification.port.NotificationPayload;
import com.oit.dondok.domain.notification.port.NotificationSender;
import com.oit.dondok.domain.notification.repository.NotificationRepository;
import com.oit.dondok.domain.notification.service.NotificationPersistingService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * FCM 알림 발송 흐름 통합 테스트.
 *
 * <p>NotificationPersistingService → FcmNotificationSenderAdapter → FcmSendEventListener 전체 흐름을
 * 검증한다. FirebaseMessaging(Firebase SDK)만 Mock 처리하여 실제 FCM 서버 호출 없이 SDK 호출 여부를 확인한다.
 *
 * <p>CI에서 제외하고 수동 실행 또는 시연 시 {@code @Disabled} 제거 후 실행.
 */
// @Disabled("FCM 알림 발송 흐름 통합 테스트 — CI 제외, 수동 실행 또는 시연 시 @Disabled 제거")
@IntegrationTest
@ActiveProfiles({"integration", "integration-fcm"})
class FcmNotificationFlowIntegrationTest {

  @Autowired private NotificationSender notificationSender;
  @Autowired private NotificationRepository notificationRepository;
  @PersistenceContext private EntityManager entityManager;
  @Autowired private PlatformTransactionManager transactionManager;

  @MockBean private FirebaseApp firebaseApp;
  @MockBean private FirebaseMessaging firebaseMessaging;

  // fcmTaskExecutor를 동기 실행으로 교체 — AFTER_COMMIT 후 즉시 FirebaseMessaging.send() 호출
  @MockBean(name = "fcmTaskExecutor")
  private TaskExecutor fcmTaskExecutor;

  private TransactionTemplate txTemplate;

  @BeforeEach
  void setUp() throws Exception {
    txTemplate = new TransactionTemplate(transactionManager);
    given(firebaseMessaging.send(any(Message.class))).willReturn("mock-message-id");
    willAnswer(
            inv -> {
              ((Runnable) inv.getArgument(0)).run();
              return null;
            })
        .given(fcmTaskExecutor)
        .execute(any(Runnable.class));
  }

  @AfterEach
  void resetMocks() {
    clearInvocations(firebaseMessaging);
  }

  @Test
  void notificationSenderIsBoundToRealPersistingService() {
    assertThat(notificationSender).isInstanceOf(NotificationPersistingService.class);
  }

  @Test
  void crewApplicationApprovedNotificationReachesFcm() throws Exception {
    Member member = persistMemberWithDevice("fcm-approved@test.com", "승인수신자", "token-approved");

    sendNotification(
        member,
        new NotificationPayload(
            "CREW_APPLICATION_APPROVED",
            "crew",
            "1",
            "dondok://crews/1",
            "테스트 크루 크루 참여 신청이 승인되었습니다.",
            "테스트 크루"));

    then(firebaseMessaging).should().send(any(Message.class));
    assertNotificationSaved(member);
  }

  @Test
  void crewApplicationRejectedNotificationReachesFcm() throws Exception {
    Member member = persistMemberWithDevice("fcm-rejected@test.com", "거절수신자", "token-rejected");

    sendNotification(
        member,
        new NotificationPayload(
            "CREW_APPLICATION_REJECTED",
            "crew",
            "1",
            "dondok://crews",
            "테스트 크루 크루 참여 신청이 거절되었습니다.",
            "테스트 크루"));

    then(firebaseMessaging).should().send(any(Message.class));
    assertNotificationSaved(member);
  }

  @Test
  void missionLogVerificationResultNotificationReachesFcm() throws Exception {
    Member member = persistMemberWithDevice("fcm-mission@test.com", "인증결과수신자", "token-mission");

    sendNotification(
        member,
        new NotificationPayload(
            "MISSION_LOG_VERIFICATION_RESULT",
            "mission_log",
            "42",
            "dondok://crews/1/certify",
            "미션 인증이 승인되었습니다.",
            "테스트 크루"));

    then(firebaseMessaging).should().send(any(Message.class));
    assertNotificationSaved(member);
  }

  @Test
  void settlementCompletedNotificationReachesFcm() throws Exception {
    Member member = persistMemberWithDevice("fcm-settlement@test.com", "정산수신자", "token-settlement");

    sendNotification(
        member,
        new NotificationPayload(
            "SETTLEMENT_COMPLETED",
            "settlement",
            "10",
            "dondok://crews/1/settlement",
            "테스트 크루 크루 정산이 완료되었습니다.",
            "테스트 크루"));

    then(firebaseMessaging).should().send(any(Message.class));
    assertNotificationSaved(member);
  }

  // 디바이스 토큰 미등록 시 예외 없이 스킵, FCM 미호출
  @Test
  void memberWithNoDeviceSkipsFcmWithoutThrowing() throws Exception {
    Member member = persistMemberWithoutDevice("fcm-nodevice@test.com", "노디바이스");

    assertThatNoException()
        .isThrownBy(
            () ->
                sendNotification(
                    member,
                    new NotificationPayload(
                        "CREW_APPLICATION_APPROVED",
                        "crew",
                        "1",
                        "dondok://crews/1",
                        "테스트 크루 참여 신청이 승인되었습니다.",
                        "테스트 크루")));

    then(firebaseMessaging).should(never()).send(any(Message.class));
    assertNotificationSaved(member);
  }

  private Member persistMemberWithDevice(String email, String nickname, String fcmToken) {
    return txTemplate.execute(
        status -> {
          Member m = Member.create(email, null, nickname);
          entityManager.persist(m);
          NotificationDevice device =
              NotificationDevice.create(
                  m, "device-" + email, NotificationPlatform.ANDROID, fcmToken, "1.0.0");
          entityManager.persist(device);
          return m;
        });
  }

  private Member persistMemberWithoutDevice(String email, String nickname) {
    return txTemplate.execute(
        status -> {
          Member m = Member.create(email, null, nickname);
          entityManager.persist(m);
          return m;
        });
  }

  // PROPAGATION.MANDATORY인 notificationSender.send()를 트랜잭션 안에서 호출한다.
  // 트랜잭션 커밋 후 AFTER_COMMIT 리스너가 동기 executor로 즉시 실행된다.
  private void sendNotification(Member member, NotificationPayload payload) {
    txTemplate.execute(
        status -> {
          notificationSender.send(member, payload);
          return null;
        });
  }

  private void assertNotificationSaved(Member member) {
    assertThat(notificationRepository.countUnread(member.getUuid())).isEqualTo(1);
  }
}
