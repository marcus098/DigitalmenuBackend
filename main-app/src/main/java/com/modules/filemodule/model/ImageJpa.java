package com.modules.filemodule.model;

import com.modules.common.model.db.Image;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "images")
public class ImageJpa extends Image {
    public ImageJpa(){
        super();
    }

    public ImageJpa(String name, long idAgency) {
        super(name, idAgency);
    }

    public ImageJpa(long id, String name, long idAgency){
        super(id, name, idAgency);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Override
    public Long getId() {
        return super.getId();
    }

    @Column(nullable = false, name = "id_agency")
    @Override
    public long getIdAgency() {
        return super.getIdAgency();
    }

    @Column(nullable = false)
    @Override
    public String getName() {
        return super.getName();
    }

    @Column(nullable = false, name="created_at")
    @Override
    public OffsetDateTime getCreatedAt() {
        return super.getCreatedAt();
    }

    @Column(nullable = false)
    @Override
    public boolean isDeleted() {
        return super.isDeleted();
    }

    @Override
    @Column(name="deleted_at")
    public OffsetDateTime getDeletedAt() {
        return super.getDeletedAt();
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
