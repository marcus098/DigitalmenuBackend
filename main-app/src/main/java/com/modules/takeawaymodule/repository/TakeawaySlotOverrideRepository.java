package com.modules.takeawaymodule.repository;

import com.modules.takeawaymodule.model.TakeawaySlotOverrideJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface TakeawaySlotOverrideRepository extends JpaRepository<TakeawaySlotOverrideJpa, Long> {
    List<TakeawaySlotOverrideJpa> findByIdAgencyAndSlotDate(Long idAgency, LocalDate slotDate);
    Optional<TakeawaySlotOverrideJpa> findByIdAgencyAndSlotDateAndSlotTime(Long idAgency, LocalDate slotDate, LocalTime slotTime);
}
