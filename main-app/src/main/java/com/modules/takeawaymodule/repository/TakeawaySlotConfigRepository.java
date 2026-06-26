package com.modules.takeawaymodule.repository;

import com.modules.takeawaymodule.model.TakeawaySlotConfigJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TakeawaySlotConfigRepository extends JpaRepository<TakeawaySlotConfigJpa, Long> {
    Optional<TakeawaySlotConfigJpa> findByIdAgency(Long idAgency);
}
