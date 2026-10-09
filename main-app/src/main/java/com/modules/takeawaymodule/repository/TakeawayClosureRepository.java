package com.modules.takeawaymodule.repository;

import com.modules.takeawaymodule.model.TakeawayClosureJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TakeawayClosureRepository extends JpaRepository<TakeawayClosureJpa, Long> {
    /** Chiusure che coprono la data (from <= date <= to). */
    List<TakeawayClosureJpa> findByIdAgencyAndFromDateLessThanEqualAndToDateGreaterThanEqual(Long idAgency, LocalDate d1, LocalDate d2);
    /** Chiusure non ancora terminate (toDate >= date), per l'elenco in dashboard. */
    List<TakeawayClosureJpa> findByIdAgencyAndToDateGreaterThanEqualOrderByFromDateAsc(Long idAgency, LocalDate date);
    Optional<TakeawayClosureJpa> findByIdAndIdAgency(Long id, Long idAgency);
}
