package com.modules.common.model.db;

import java.time.OffsetDateTime;

public abstract class Image {
    protected Long id;
    protected String name;
    protected OffsetDateTime createdAt;
    protected OffsetDateTime deletedAt;
    protected boolean deleted;
    protected long idAgency;

    public Image() {

    }

    public Image(String name, long idAgency) {
        this.name = name;
        this.idAgency = idAgency;
        this.createdAt = OffsetDateTime.now();
        this.deleted = false;
    }

    public Image(long id, String name, long idAgency) {
        this.name = name;
        this.id = id;
        this.idAgency = idAgency;
        this.createdAt = OffsetDateTime.now();
        this.deleted = false;
    }

    public long getIdAgency() {
        return idAgency;
    }

    public String getName() {
        return name;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public Long getId() {
        return id;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setIdAgency(long idAgency) {
        this.idAgency = idAgency;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    @Override
    public String toString() {
        return "Image{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", createdAt=" + createdAt +
                ", deletedAt=" + deletedAt +
                ", deleted=" + deleted +
                ", idAgency=" + idAgency +
                '}';
    }
}
