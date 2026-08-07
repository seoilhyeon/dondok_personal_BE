package com.oit.dondok.infra.loadtest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the recovery race only in the explicit local load-test profile. */
@Component
@Profile("load-test & !prod")
class LoadTestRecoveryCompletionMismatchHook {

  @PersistenceContext private EntityManager entityManager;

  @Transactional
  void changeOrderAfterLookup(String paymentId) {
    entityManager
        .createQuery(
            "update PointCharge charge set charge.orderId = concat(charge.orderId, '-changed') "
                + "where charge.paymentId = :paymentId")
        .setParameter("paymentId", paymentId)
        .executeUpdate();
  }
}
