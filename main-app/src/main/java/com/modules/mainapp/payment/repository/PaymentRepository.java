package com.modules.mainapp.payment.repository;

import com.modules.mainapp.payment.entity.PaymentJpa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentJpa, Long> {
    Optional<PaymentJpa> findByStripePaymentIntentId(String stripePaymentIntentId);
    List<PaymentJpa> findByIdAgencyOrderByCreatedAtDesc(long idAgency);
    List<PaymentJpa> findByIdAgencyAndCreatedAtBetweenAndStatus(long idAgency, LocalDateTime from, LocalDateTime to, String status);
    boolean existsByComandIdAndStatus(String comandId, String status);
    Optional<PaymentJpa> findFirstByComandIdAndStatusOrderByCreatedAtDesc(String comandId, String status);

    /** Transizione atomica (idempotenza webhook): ritorna 0 se il pagamento era già nello stato richiesto. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PaymentJpa p SET p.status = :status, p.updatedAt = :now " +
            "WHERE p.stripePaymentIntentId = :intentId AND p.status <> :status")
    int updateStatusIfDifferent(@Param("intentId") String intentId, @Param("status") String status, @Param("now") LocalDateTime now);
}
