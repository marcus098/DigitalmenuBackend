package com.modules.ingredientmodule.repository;

import com.modules.ingredientmodule.model.IngredientJpa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface IngredientRepository extends JpaRepository<IngredientJpa, Long> {
    List<IngredientJpa> findAllByIdAgencyAndDeletedOrderByName(Long idAgency, boolean deleted);
    List<IngredientJpa> findAllByIdAgencyAndDeletedAndAddableOrderByName(Long idAgency, boolean deleted, boolean addable);
    List<IngredientJpa> findAllByIdInAndDeleted(List<Long> ids, boolean deleted);
    Optional<IngredientJpa> findByIdAndDeletedAndIdAgency(Long id, boolean deleted, Long idAgency);
    List<IngredientJpa> findAllByIdInAndIdAgencyAndDeleted(Set<Long> ids, Long idAgency, boolean deleted);
    List<IngredientJpa> findAllByIdInAndIdAgencyAndDeleted(List<Long> ids, Long idAgency, boolean deleted);
}
