package com.modules.authmodule.model.superadmin;

import jakarta.persistence.*;

import java.time.Instant;

/** Nota interna su un locale: visibile SOLO ai superadmin. */
@Entity
@Table(name = "agency_internal_notes", indexes = @Index(name = "idx_agency_notes_agency", columnList = "id_agency"))
public class AgencyNoteJpa {

    public static final int MAX_TEXT = 4000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_agency", nullable = false)
    private Long idAgency;

    @Column(name = "text", nullable = false, length = MAX_TEXT)
    private String text;

    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "author_name")
    private String authorName;

    @Column(name = "author_email")
    private String authorEmail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "pinned", nullable = false)
    private boolean pinned;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getIdAgency() { return idAgency; }
    public void setIdAgency(Long idAgency) { this.idAgency = idAgency; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public String getAuthorEmail() { return authorEmail; }
    public void setAuthorEmail(String authorEmail) { this.authorEmail = authorEmail; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
}
