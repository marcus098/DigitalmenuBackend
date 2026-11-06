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
    /** NAME / DESCRIPTION; per VARIANT l'id del gruppo ("impasto") o gruppo/scelta ("impasto/senza-glutine"). */
    @Column(length = 128)
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
        this.field = key.field();
        this.lang = lang;
    }

    public TranslationKey key() {
        return new TranslationKey(EntityType.valueOf(entityType), entityId, field);
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

    /** VARIANT: nomi di gruppi e scelte delle varianti di un prodotto (id = id prodotto). */
    public enum EntityType { CATEGORY, PRODUCT, INGREDIENT, VARIANT }
    public enum Field { NAME, DESCRIPTION }

    /**
     * Testo del menu da tradurre. field = nome di {@link Field} per CATEGORY/PRODUCT/INGREDIENT; per VARIANT
     * è l'id del gruppo (nome del gruppo) oppure "idGruppo/idScelta" (nome della scelta).
     */
    public record TranslationKey(EntityType type, long id, String field) {
        public TranslationKey(EntityType type, long id, Field field) {
            this(type, id, field.name());
        }

        public static String variantField(String groupId, String choiceId) {
            return choiceId == null ? groupId : groupId + "/" + choiceId;
        }
    }
}
