package com.modules.categorymodule.repository;

import com.modules.categorymodule.model.CategoryJpa;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CategoryRepository extends JpaRepository<CategoryJpa, Long> {
    List<CategoryJpa> findAllByIdAgencyAndDeletedOrderByPositionProgressive(Long idAgency, boolean deleted);

    List<CategoryJpa> findByIdInAndIdAgencyAndDeleted(List<Long> ids, long agencyId, boolean deleted);

    List<CategoryJpa> findAllByIdInAndIdAgencyAndDeleted(Set<Long> ids, long idAgency, boolean deleted);

    @Query("SELECT MAX(c.positionProgressive) FROM CategoryJpa c WHERE c.idAgency = :idAgency AND c.deleted = false")
    Integer findMaxPositionByAgency(@Param("idAgency") Long idAgency);

    @Modifying
    @Query("UPDATE CategoryJpa c SET c.positionProgressive = c.positionProgressive + 1 " +
            "WHERE c.idAgency = :idAgency AND c.positionProgressive BETWEEN :start AND :end AND c.deleted = false")
    void incrementPositions(@Param("idAgency") Long idAgency, @Param("start") int start, @Param("end") int end);

    @Modifying
    @Query("UPDATE CategoryJpa c SET c.positionProgressive = c.positionProgressive - 1 " +
            "WHERE c.idAgency = :idAgency AND c.positionProgressive BETWEEN :start AND :end AND c.deleted = false")
    void decrementPositions(@Param("idAgency") Long idAgency, @Param("start") int start, @Param("end") int end);

    Optional<CategoryJpa> findByIdAndIdAgencyAndDeleted(long id, long idAgency, boolean deleted);

    @Modifying
    @Transactional
    @Query("UPDATE CategoryJpa c SET c.image = '' WHERE c.deleted = false and c.image = :imageName")
    void updateImageToEmpty(String imageName);

}
