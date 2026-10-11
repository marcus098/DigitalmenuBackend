package com.modules.cardmodule.repository;

import com.modules.cardmodule.models.CardMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CardMovementRepository extends JpaRepository<CardMovement, Long> {
    List<CardMovement> findTop200ByIdCardAndIdAgencyOrderByCreatedAtDesc(long idCard, long idAgency);
}
