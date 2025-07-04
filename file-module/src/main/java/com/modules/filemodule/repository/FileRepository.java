package com.modules.filemodule.repository;

import com.modules.filemodule.model.FileJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<FileJpa, Long> {
    Optional<FileJpa> findByIdAndIdAgencyAndDeleted(long id, long idAgency, boolean deleted);
    List<FileJpa> findAllByIdAgencyAndDeletedAndParentFolder(long idAgency, boolean deleted, long parent);
}
