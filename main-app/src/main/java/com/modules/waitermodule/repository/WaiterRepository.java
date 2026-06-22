package com.modules.waitermodule.repository;

import com.modules.waitermodule.model.WaiterJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WaiterRepository extends JpaRepository<WaiterJpa, Long> {
    Optional<WaiterJpa> findByIdAndIdAgencyAndDeleted(long id, long idAgency, boolean deleted);
}
