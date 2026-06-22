package com.modules.tablemodule.repository;

import com.modules.tablemodule.model.TableEntityJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

public interface TableEntityRepository extends JpaRepository<TableEntityJpa, Long> {
    List<TableEntityJpa> findAllByIdAgencyAndDeleted(long idAgency, boolean deleted);
    Optional<TableEntityJpa> findByIdAndIdAgencyAndDeleted(long id, long idAgency, boolean deleted);
    List<TableEntityJpa> findAllByLocationAndIdAgencyAndDeleted(String location, long idAgency, boolean deleted);
    List<TableEntityJpa> findByIdInAndIdAgencyAndDeleted(List<Long> ids, long idAgency, boolean deleted);
}
