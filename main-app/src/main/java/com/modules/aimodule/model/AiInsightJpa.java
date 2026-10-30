package com.modules.aimodule.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Riepilogo/consiglio AxiomAI di un locale per un periodo. I numeri (facts, highlights) sono calcolati in Java;
 * {@code text} è scritto dall'AI ({@code aiGenerated=true}) o, se Ollama non è disponibile, da Java.
 * Con status PENDING {@code generatedAt} è il momento della richiesta.
 */
@Entity
@Table(name = "ai_insights",
        uniqueConstraints = @UniqueConstraint(name = "uk_ai_insight_period",
                columnNames = {"id_agency", "kind", "period_from"}))
public class AiInsightJpa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "id_agency", nullable = false)
    private long idAgency;
    @Column(name = "kind", nullable = false, length = 16)
    private String kind;
    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;
    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;
    @Column(name = "status", nullable = false, length = 16)
    private String status;
    @Column(name = "text", columnDefinition = "TEXT")
    private String text;
    @Column(name = "highlights_json", columnDefinition = "TEXT")
    private String highlightsJson;
    @Column(name = "facts_json", columnDefinition = "TEXT")
    private String factsJson;
    @Column(name = "model", length = 100)
    private String model;
    @Column(name = "ai_generated")
    private Boolean aiGenerated;
    @Column(name = "generated_at")
    private LocalDateTime generatedAt;
    @Column(name = "error", length = 500)
    private String error;

    public AiInsightJpa() {}

    public AiInsightJpa(long idAgency, InsightKind kind, LocalDate periodFrom, LocalDate periodTo) {
        this.idAgency = idAgency;
        this.kind = kind.name();
        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getIdAgency() { return idAgency; }
    public void setIdAgency(long idAgency) { this.idAgency = idAgency; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public LocalDate getPeriodFrom() { return periodFrom; }
    public void setPeriodFrom(LocalDate periodFrom) { this.periodFrom = periodFrom; }
    public LocalDate getPeriodTo() { return periodTo; }
    public void setPeriodTo(LocalDate periodTo) { this.periodTo = periodTo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getHighlightsJson() { return highlightsJson; }
    public void setHighlightsJson(String highlightsJson) { this.highlightsJson = highlightsJson; }
    public String getFactsJson() { return factsJson; }
    public void setFactsJson(String factsJson) { this.factsJson = factsJson; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public boolean isAiGenerated() { return Boolean.TRUE.equals(aiGenerated); }
    public void setAiGenerated(boolean aiGenerated) { this.aiGenerated = aiGenerated; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
