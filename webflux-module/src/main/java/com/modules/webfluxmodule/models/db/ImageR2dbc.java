package com.modules.webfluxmodule.models.db;

import com.modules.common.model.db.Image;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Table("images")
public class ImageR2dbc extends Image {
    public ImageR2dbc(){
        super();
    }

    public ImageR2dbc(String name, long idAgency) {
        super(name, idAgency);
    }

    public ImageR2dbc(long id, String name, long idAgency){
        super(id, name, idAgency);
    }

    @Id
    @Override
    public Long getId() {
        return super.getId();
    }

    @Override
    public long getIdAgency() {
        return super.getIdAgency();
    }

    @Override
    public String getName() {
        return super.getName();
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
    public boolean isDeleted() {
        return super.isDeleted();
    }

    @Override
    public void setIdAgency(long idAgency) {
        super.setIdAgency(idAgency);
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
    public String toString() {
        return super.toString();
    }
}
