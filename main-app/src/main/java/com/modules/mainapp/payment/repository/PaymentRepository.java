package com.modules.mainapp.payment.repository;

import com.modules.mainapp.payment.entity.PaymentJpa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentJpa, Long> {
    Optional<PaymentJpa> findByStripePaymentIntentId(String stripePaymentIntentId);
    List<PaymentJpa> findByIdAgencyOrderByCreatedAtDesc(long idAgency);
    List<PaymentJpa> findByIdAgencyAndCreatedAtBetweenAndStatus(long idAgency, LocalDateTime from, LocalDateTime to, String status);
}
