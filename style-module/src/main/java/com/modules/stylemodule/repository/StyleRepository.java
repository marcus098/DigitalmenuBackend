package com.modules.stylemodule.repository;

import com.modules.stylemodule.models.StyleJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StyleRepository extends JpaRepository<StyleJpa, Integer> {
    Optional<StyleJpa> findByIdAgencyAndDeleted(long idAgency, boolean deleted);
}
