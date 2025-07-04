package com.modules.webfluxmodule.models.db;

import com.modules.common.model.db.Category;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Table("categories")
public class CategoryR2dbc extends Category {
    public CategoryR2dbc() {
        super();
    }

    public CategoryR2dbc(String name, String description, Long idAgency, int positionProgressive, boolean available, String image) {
        super(name, description, idAgency, positionProgressive, available, image);
    }

    public CategoryR2dbc(long id, String name, String description, Long idAgency, int positionProgressive, boolean available, String image) {
        super(id, name, description, idAgency, positionProgressive, available, image);
    }
    @Id
    @Override
    public Long getId() {
        return super.getId();
    }

    @Override
    public String getImage() {
        return super.getImage();
    }

    @Override
    public void setImage(String image) {
        super.setImage(image);
    }

    @Override
    public boolean isDeleted() {
        return super.isDeleted();
    }

    @Override
    public boolean isAvailable() {
        return super.isAvailable();
    }

    @Override
    public void setAvailable(boolean available) {
        super.setAvailable(available);
    }

    @Override
    public int getPositionProgressive() {
        return super.getPositionProgressive();
    }

    @Override
    public void setPositionProgressive(int positionProgressive) {
        super.setPositionProgressive(positionProgressive);
    }

    @Override
    public long getIdAgency() {
        return super.getIdAgency();
    }

    @Override
    public OffsetDateTime getCreatedAt() {
        return super.getCreatedAt();
    }

    @Override
    public OffsetDateTime getDeletedAt() {
        return super.getDeletedAt();
    }

    @Override
    public void setIdAgency(Long idAgency) {
        super.setIdAgency(idAgency);
    }

    @Override
    public void setCreatedAt(OffsetDateTime createdAt) {
        super.setCreatedAt(createdAt);
    }

    @Override
    public void setDeleted(boolean deleted) {
        super.setDeleted(deleted);
    }

    @Override
    public void setDeletedAt(OffsetDateTime deletedAt) {
        super.setDeletedAt(deletedAt);
    }

    @Override
    public void setIdAgency(long idAgency) {
        super.setIdAgency(idAgency);
    }

    @Override
    public String getName() {
        return super.getName();
    }

    @Override
    public String getDescription() {
        return super.getDescription();
    }

    @Override
    public void setId(Long id) {
        super.setId(id);
    }

    @Override
    public void setName(String name) {
        super.setName(name);
    }

    @Override
    public void setDescription(String description) {
        super.setDescription(description);
    }
}
