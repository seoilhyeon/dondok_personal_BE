package com.oit.dondok.infra.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.config.JpaAuditingConfig;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.domain.point.entity.PointCharge;
import com.oit.dondok.domain.point.repository.PointChargeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest(
    properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@ActiveProfiles("load-test")
@Import({JpaAuditingConfig.class, LoadTestRecoveryCompletionMismatchHook.class})
class LoadTestRecoveryCompletionMismatchHookTest {

  @Autowired private MemberRepository memberRepository;
  @Autowired private PointChargeRepository pointChargeRepository;
  @Autowired private LoadTestRecoveryCompletionMismatchHook hook;
  @Autowired private TestEntityManager entityManager;

  @Test
  void changesTheStoredOrderOnlyAfterTheLookupFixtureRequestsIt() {
    Member member =
        memberRepository.save(Member.create("load-hook@local.invalid", null, "load-hook"));
    pointChargeRepository.save(
        PointCharge.createPending(
            member, "load-test-completion-mismatch", "load-test-order", 10_000L));

    hook.changeOrderAfterLookup("load-test-completion-mismatch");
    entityManager.clear();

    assertThat(pointChargeRepository.findByPaymentId("load-test-completion-mismatch"))
        .get()
        .extracting(PointCharge::getOrderId)
        .isEqualTo("load-test-order-changed");
  }
}
