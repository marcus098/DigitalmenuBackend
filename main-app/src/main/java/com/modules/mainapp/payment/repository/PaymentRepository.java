package com.modules.mainapp.payment.repository;

import com.modules.mainapp.payment.entity.PaymentJpa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentJpa, Long> {
    Optional<PaymentJpa> findByStripePaymentIntentId(String stripePaymentIntentId);
    Optional<PaymentJpa> findBySumupCheckoutId(String sumupCheckoutId);
    List<PaymentJpa> findByIdAgencyOrderByCreatedAtDesc(long idAgency);
    List<PaymentJpa> findByIdAgencyAndCreatedAtBetweenAndStatus(long idAgency, LocalDateTime from, LocalDateTime to, String status);
    boolean existsByComandIdAndStatus(String comandId, String status);
    Optional<PaymentJpa> findFirstByComandIdAndStatusOrderByCreatedAtDesc(String comandId, String status);
    List<PaymentJpa> findByComandIdAndStatusInOrderByCreatedAtDesc(String comandId, Collection<String> statuses);
    Optional<PaymentJpa> findByIdAndIdAgency(long id, long idAgency);
    long countByComandId(String comandId);

    /** Transizione condizionale: aggiorna solo se lo stato attuale è tra quelli ammessi (0 = già transitato). */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PaymentJpa p SET p.status = :status, p.updatedAt = :now " +
            "WHERE p.id = :id AND p.status IN :from")
    int updateStatusIfIn(@Param("id") long id, @Param("from") Collection<String> from,
                         @Param("status") String status, @Param("now") LocalDateTime now);

    /** Come {@link #updateStatusIfIn} verso AUTHORIZED, impostando anche captured_upfront. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PaymentJpa p SET p.status = :status, p.capturedUpfront = :upfront, p.updatedAt = :now " +
            "WHERE p.id = :id AND p.status IN :from")
    int authorizeIfIn(@Param("id") long id, @Param("from") Collection<String> from, @Param("status") String status,
                      @Param("upfront") boolean capturedUpfront, @Param("now") LocalDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PaymentJpa p SET p.sumupTransactionId = :tx, p.updatedAt = :now WHERE p.id = :id")
    int setSumupTransactionId(@Param("id") long id, @Param("tx") String transactionId, @Param("now") LocalDateTime now);
}
