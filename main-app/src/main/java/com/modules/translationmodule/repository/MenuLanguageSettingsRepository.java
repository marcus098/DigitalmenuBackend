package com.modules.translationmodule.repository;

import com.modules.translationmodule.model.MenuLanguageSettingsJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuLanguageSettingsRepository extends JpaRepository<MenuLanguageSettingsJpa, Long> {
    List<MenuLanguageSettingsJpa> findAllByAutoTranslateTrue();
}
