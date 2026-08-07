package com.oit.dondok.infra.loadtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.oit.dondok.domain.point.port.PaymentLookupResult;
import org.junit.jupiter.api.Test;

class LoadTestPaymentConfirmClientTest {

  private final LoadTestRecoveryCompletionMismatchHook recoveryCompletionMismatchHook =
      mock(LoadTestRecoveryCompletionMismatchHook.class);
  private final LoadTestPaymentConfirmClient client =
      new LoadTestPaymentConfirmClient(recoveryCompletionMismatchHook);

  @Test
  void completionMismatchKeepsLookupCanonicalThenMutatesBeforeRecoveryCompletion() {
    String paymentId = "load-test-recover-completion-mismatch";

    PaymentLookupResult result = client.lookup(paymentId);

    assertThat(result)
        .isEqualTo(
            new PaymentLookupResult(
                paymentId, "load-test-order-recover-completion-mismatch", 10_000L, "KRW", "DONE"));
    then(recoveryCompletionMismatchHook).should().changeOrderAfterLookup(paymentId);
  }

  @Test
  void ordinaryLookupMismatchDoesNotUsePostLookupMutationHook() {
    client.lookup("load-test-recover-mismatch");

    then(recoveryCompletionMismatchHook).shouldHaveNoInteractions();
  }
}
