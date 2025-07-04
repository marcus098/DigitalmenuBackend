package com.modules.cardmodule.repository;

import com.modules.cardmodule.models.CardJpa;
import com.modules.common.model.db.Card;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CardRepository extends JpaRepository<CardJpa, Long> {
    Optional<CardJpa> findByCodeAndDeleted(String code, boolean deleted);
    Optional<CardJpa> findByIdAndDeletedAndIdAgency(long id, boolean deleted, long idAgency);
    Optional<CardJpa> findByIdAndDeleted(long id, boolean deleted);
    List<CardJpa> findAllByIdAgencyAndDeleted(long idAgency, boolean deleted);
    boolean existsByCode(String code);
}
