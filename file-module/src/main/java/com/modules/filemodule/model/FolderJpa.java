package com.modules.filemodule.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "folders")
public class FolderJpa {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    private String name;
    private OffsetDateTime createdAt;
    private OffsetDateTime deletedAt;
    private boolean deleted = false;
    private long idAgency;

    public FolderJpa() {}

    public FolderJpa(long id, String name, OffsetDateTime createdAt, OffsetDateTime deletedAt, boolean deleted, long idAgency) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.deletedAt = deletedAt;
        this.deleted = deleted;
        this.idAgency = idAgency;
    }

    public FolderJpa(String name, OffsetDateTime createdAt, OffsetDateTime deletedAt, boolean deleted, long idAgency) {
        this.name = name;
        this.createdAt = createdAt;
        this.deletedAt = deletedAt;
        this.deleted = deleted;
        this.idAgency = idAgency;
    }

    public long getIdAgency() {
        return idAgency;
    }

    public void setIdAgency(long idAgency) {
        this.idAgency = idAgency;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    @Override
    public String toString() {
        return "FolderJpa{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", createdAt=" + createdAt +
                ", deletedAt=" + deletedAt +
                ", deleted=" + deleted +
                '}';
    }
}
