package com.modules.deliverymodule.repository;

import com.modules.deliverymodule.model.DeliverySettingsJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeliverySettingsRepository extends JpaRepository<DeliverySettingsJpa, Long> {
    Optional<DeliverySettingsJpa> findByIdAgency(Long idAgency);
}
