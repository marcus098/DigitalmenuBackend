package com.modules.authmodule.repository;

import com.modules.authmodule.model.AgencyJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgencyRepository extends JpaRepository<AgencyJpa, Long> {
    Optional<AgencyJpa> findByIdAndDeleted(Long id, Boolean deleted);
    boolean existsByNameAndDeleted(String name, boolean deleted);
}
