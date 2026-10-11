package com.modules.translationmodule.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Traduzione di un testo del menu (nome o descrizione di categoria, prodotto, ingrediente) in una lingua.
 * {@code sourceHash} è l'impronta del testo italiano tradotto: se il testo cambia, la traduzione è "da aggiornare".
 */
@Entity
@Table(name = "menu_translations",
        uniqueConstraints = @UniqueConstraint(name = "uk_menu_translation",
                columnNames = {"idAgency", "entityType", "entityId", "lang", "field"}),
        indexes = @Index(name = "idx_menu_translation_agency_lang", columnList = "idAgency, lang"))
public class MenuTranslationJpa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private long idAgency;
    @Column(length = 16)
    private String entityType;
    private long entityId;
    @Column(length = 8)
    private String lang;
    @Column(length = 16)
    private String field;
    @Column(columnDefinition = "TEXT")
    private String text;
    @Column(length = 64)
    private String sourceHash;
    /** Corretta a mano dal locale: la traduzione automatica non la sovrascrive. */
    private Boolean manual;
    private OffsetDateTime updatedAt;

    public MenuTranslationJpa() {}

    public MenuTranslationJpa(long idAgency, TranslationKey key, String lang) {
        this.idAgency = idAgency;
        this.entityType = key.type().name();
        this.entityId = key.id();
        this.field = key.field().name();
        this.lang = lang;
    }

    public TranslationKey key() {
        return new TranslationKey(EntityType.valueOf(entityType), entityId, Field.valueOf(field));
    }

    public Long getId() { return id; }
    public long getIdAgency() { return idAgency; }
    public String getEntityType() { return entityType; }
    public long getEntityId() { return entityId; }
    public String getLang() { return lang; }
    public String getField() { return field; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getSourceHash() { return sourceHash; }
    public void setSourceHash(String sourceHash) { this.sourceHash = sourceHash; }
    public boolean isManual() { return Boolean.TRUE.equals(manual); }
    public void setManual(boolean manual) { this.manual = manual ? Boolean.TRUE : null; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public enum EntityType { CATEGORY, PRODUCT, INGREDIENT }
    public enum Field { NAME, DESCRIPTION }

    /** Testo del menu da tradurre. */
    public record TranslationKey(EntityType type, long id, Field field) {}
}
