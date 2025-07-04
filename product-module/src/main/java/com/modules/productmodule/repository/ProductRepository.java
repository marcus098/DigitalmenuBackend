package com.modules.productmodule.repository;

import com.modules.productmodule.model.ProductJpa;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ProductRepository extends JpaRepository<ProductJpa, Long> {
    @Query("SELECT MAX(p.positionProgressive) FROM ProductJpa p WHERE p.idAgency = :idAgency AND p.idCategory = :idCategory AND p.deleted = false")
    Integer findMaxPositionByIdAgencyAndIdCategory(@Param("idAgency") Long idAgency, @Param("idCategory") Long idCategory);

    List<ProductJpa> findAllByIdInAndIdAgencyAndDeleted(Set<Long> ids, Long idAgency, Boolean deleted);

    List<ProductJpa> findAllByIdCategoryAndDeletedAndIdAgency(long idCategory, boolean deleted, long idAgency);

    List<ProductJpa> findAllByDeletedAndIdAgency(boolean deleted, long idAgency);

    @Query(value = "SELECT * FROM products WHERE id_agency = :idAgency AND deleted = false AND ingredients LIKE %:ingredientId% AND ingredients LIKE CONCAT('%|', :ingredientId, '|%')", nativeQuery = true)
    List<ProductJpa> findAllByIngredientId(@Param("ingredientId") long ingredientId, @Param("idAgency") long idAgency);

    Optional<ProductJpa> findByIdAndIdAgencyAndDeleted(long idCategory, long idAgency, boolean deleted);

    @Modifying
    @Query("UPDATE ProductJpa p SET p.positionProgressive = p.positionProgressive + 1 " +
            "WHERE p.idAgency = :idAgency AND p.idCategory = :idCategory AND p.positionProgressive BETWEEN :start AND :end AND p.deleted = false")
    void incrementPositions(@Param("idAgency") Long idAgency, @Param("idCategory") long idCategory, @Param("start") int start, @Param("end") int end);

    @Modifying
    @Query("UPDATE ProductJpa p SET p.positionProgressive = p.positionProgressive - 1 " +
            "WHERE p.idAgency = :idAgency AND p.idCategory = :idCategory AND p.positionProgressive BETWEEN :start AND :end AND p.deleted = false")
    void decrementPositions(@Param("idAgency") Long idAgency, @Param("idCategory") long idCategory, @Param("start") int start, @Param("end") int end);

    @Modifying
    @Query("UPDATE ProductJpa p SET p.idCategory = :newCategory WHERE p.id IN :ids")
    void updateCategoryForIds(@Param("newCategory") long newCategory, @Param("ids") List<Long> ids1);

    @Query("SELECT p FROM ProductJpa p WHERE p.id IN :ids AND p.deleted = false AND p.idAgency = :idAgency")
    List<ProductJpa> findProductsByConditions(
            @Param("ids") List<Long> ids,
            @Param("idAgency") long idAgency
    );

    @Modifying
    @Transactional
    @Query("UPDATE ProductJpa p SET p.image = '' WHERE p.deleted = false and p.image = :imageName")
    void updateImageToEmpty(String imageName);

}
