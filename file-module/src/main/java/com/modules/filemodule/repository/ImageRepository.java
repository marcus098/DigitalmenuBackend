package com.modules.filemodule.repository;

import com.modules.filemodule.model.ImageJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImageRepository extends JpaRepository<ImageJpa, Integer> {
    List<ImageJpa> findAllByIdAgencyAndDeleted(long idAgency, boolean deleted);
    Optional<ImageJpa> findByIdAndIdAgencyAndDeleted(long id, long idAgency, boolean deleted);
    Optional<ImageJpa> findByNameAndIdAgencyAndDeleted(String name, long idAgency, boolean deleted);
}
