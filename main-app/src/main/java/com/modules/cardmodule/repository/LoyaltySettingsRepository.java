package com.modules.cardmodule.repository;

import com.modules.cardmodule.models.LoyaltySettingsJpa;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoyaltySettingsRepository extends JpaRepository<LoyaltySettingsJpa, Long> {
}
