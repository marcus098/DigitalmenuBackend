package com.modules.translationmodule.repository;

import com.modules.translationmodule.model.MenuTranslationJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MenuTranslationRepository extends JpaRepository<MenuTranslationJpa, Long> {
    List<MenuTranslationJpa> findAllByIdAgencyAndLang(long idAgency, String lang);
    List<MenuTranslationJpa> findAllByIdAgency(long idAgency);
    Optional<MenuTranslationJpa> findByIdAgencyAndEntityTypeAndEntityIdAndLangAndField(long idAgency, String entityType, long entityId, String lang, String field);
}
