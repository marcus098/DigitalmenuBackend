package com.modules.mainapp.payment.repository;

import com.modules.mainapp.payment.entity.AgencyPaymentAccountJpa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgencyPaymentAccountRepository extends JpaRepository<AgencyPaymentAccountJpa, Long> {
    Optional<AgencyPaymentAccountJpa> findByWebhookToken(String webhookToken);
}
