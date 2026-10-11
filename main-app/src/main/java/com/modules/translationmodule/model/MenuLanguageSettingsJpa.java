package com.modules.translationmodule.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** Lingue in cui il locale mostra il menu (l'italiano c'è sempre). */
@Entity
@Table(name = "menu_language_settings")
public class MenuLanguageSettingsJpa {

    @Id
    private Long idAgency;
    /** Codici separati da virgola, es. "en,de,fr". */
    @Column(length = 100)
    private String languages;
    /** Traduce di notte i testi nuovi o modificati. */
    private Boolean autoTranslate;
    private OffsetDateTime updatedAt;

    public MenuLanguageSettingsJpa() {}
    public MenuLanguageSettingsJpa(Long idAgency) { this.idAgency = idAgency; }

    public Long getIdAgency() { return idAgency; }
    public String getLanguages() { return languages; }
    public void setLanguages(String languages) { this.languages = languages; }
    public boolean isAutoTranslate() { return Boolean.TRUE.equals(autoTranslate); }
    public void setAutoTranslate(boolean autoTranslate) { this.autoTranslate = autoTranslate; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
