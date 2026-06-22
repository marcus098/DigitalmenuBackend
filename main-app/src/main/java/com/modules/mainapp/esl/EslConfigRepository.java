package com.modules.mainapp.esl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EslConfigRepository extends JpaRepository<EslConfigJpa, Long> {
    Optional<EslConfigJpa> findByTableId(Long tableId);
    List<EslConfigJpa> findByIdAgency(Long idAgency);
    void deleteByTableId(Long tableId);
}
