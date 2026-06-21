package com.oit.dondok.infra.fcm.config;

/**
 * FCM profile policy.
 *
 * <p>Default test and integration contexts use StubNotificationSender to avoid Firebase credentials
 * and external push delivery. The integration-fcm profile is an explicit opt-in layered on
 * integration for tests that verify real FCM wiring with Firebase SDK beans mocked. Non-test,
 * non-integration runtime profiles use real FCM components when app.firebase.credentials-path is
 * configured.
 */
public final class FcmProfilePolicy {

  public static final String REAL_FCM = "(!test & !integration) | integration-fcm";
  public static final String STUB_FCM = "(test | integration) & !integration-fcm";

  private FcmProfilePolicy() {}
}
