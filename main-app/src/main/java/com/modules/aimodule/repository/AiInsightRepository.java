package com.modules.aimodule.repository;

import com.modules.aimodule.model.AiInsightJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface AiInsightRepository extends JpaRepository<AiInsightJpa, Long> {
    Optional<AiInsightJpa> findByIdAgencyAndKindAndPeriodFrom(long idAgency, String kind, LocalDate periodFrom);
}
