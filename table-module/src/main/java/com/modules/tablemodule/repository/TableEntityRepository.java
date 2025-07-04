package com.modules.tablemodule.repository;

import com.modules.tablemodule.model.TableEntityJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TableEntityRepository extends JpaRepository<TableEntityJpa, Integer> {
    List<TableEntityJpa> findAllByIdAgencyAndDeleted(long idAgency, boolean deleted);
    Optional<TableEntityJpa> findByIdAndIdAgencyAndDeleted(long id, long idAgency, boolean deleted);
}
