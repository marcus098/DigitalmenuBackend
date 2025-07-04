package com.modules.common.model.db;

import java.time.OffsetDateTime;

public abstract class Category {
    protected Long id;
    protected String name;
    protected String description;
    protected Long idAgency;
    protected boolean deleted;
    protected OffsetDateTime createdAt;
    protected OffsetDateTime deletedAt;
    protected int positionProgressive;
    protected boolean available;
    protected String image;

    public Category() {
    }

    //public Category(AddCategory addCategory, long idAgency, int positionProgressive){
    //    this.name = addCategory.getName();
    //    this.description = addCategory.getDescription();
    //    this.idAgency = idAgency;
    //    this.deleted = false;
    //    this.createdAt = OffsetDateTime.now();
    //    this.available = addCategory.isAvailable();
    //    this.positionProgressive = positionProgressive;
    //    this.image = addCategory.getImage();
    //}

    public Category(String name, String description, Long idAgency, int positionProgressive, boolean available, String image) {
        this.name = name;
        this.description = description;
        this.idAgency = idAgency;
        this.deleted = false;
        this.image = image;
        this.createdAt = OffsetDateTime.now();
        this.available = available;
        this.positionProgressive = positionProgressive;
    }

    public Category(long id, String name, String description, Long idAgency, int positionProgressive, boolean available, String image) {
        this.id = id;
        this.image = image;
        this.available = available;
        this.name = name;
        this.description = description;
        this.idAgency = idAgency;
        this.deleted = false;
        this.createdAt = OffsetDateTime.now();
        this.positionProgressive = positionProgressive;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public int getPositionProgressive() {
        return positionProgressive;
    }

    public void setPositionProgressive(int positionProgressive) {
        this.positionProgressive = positionProgressive;
    }

    public long getIdAgency() {
        return idAgency;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setIdAgency(Long idAgency) {
        this.idAgency = idAgency;
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

    public void setIdAgency(long idAgency) {
        this.idAgency = idAgency;
    }

    public String getName() {
        return name;
    }

    public Long getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
