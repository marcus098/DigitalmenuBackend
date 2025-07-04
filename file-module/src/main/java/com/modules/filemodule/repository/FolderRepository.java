package com.modules.filemodule.repository;

import com.modules.filemodule.model.FolderJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<FolderJpa, Long> {
    Optional<FolderJpa> findByIdAndIdAgencyAndDeleted(long id, long idAgency, boolean deleted);
    List<FolderJpa> findAllByIdAgencyAndDeleted(long idAgency, boolean deleted);
}
