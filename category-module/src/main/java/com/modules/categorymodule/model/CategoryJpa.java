package com.modules.categorymodule.model;

import com.modules.categorymodule.requests.AddCategory;
import com.modules.common.model.db.Category;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "categories")
public class CategoryJpa extends Category {

    public CategoryJpa() {
        super();
    }

    public CategoryJpa(AddCategory addCategory, long idAgency, int positionProgressive) {
        super(addCategory.getName(), addCategory.getDescription(), idAgency, positionProgressive, addCategory.isAvailable(), addCategory.getImage());
    }

    public CategoryJpa(String name, String description, Long idAgency, int positionProgressive, boolean available, String image) {
        super(name, description, idAgency, positionProgressive, available, image);
    }

    public CategoryJpa(long id, String name, String description, Long idAgency, int positionProgressive, boolean available, String image) {
        super(id, name, description, idAgency, positionProgressive, available, image);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Override
    public Long getId() {
        return super.getId();
    }

    @Column(nullable = false, name="id_agency")
    @Override
    public long getIdAgency() {
        return super.getIdAgency();
    }

    @Column(nullable = false)
    @Override
    public String getName() {
        return super.getName();
    }

    @Column(nullable = false)
    @Override
    public boolean isDeleted() {
        return super.isDeleted();
    }

    @Column(nullable = false, name="created_at")
    @Override
    public OffsetDateTime getCreatedAt() {
        return super.getCreatedAt();
    }

    @Column(nullable = false, name = "position_progressive")
    @Override
    public int getPositionProgressive() {
        return super.getPositionProgressive();
    }

    @Column(nullable = false)
    @Override
    public boolean isAvailable() {
        return super.isAvailable();
    }

    @Override
    @Column(name="deleted_at")
    public OffsetDateTime getDeletedAt() {
        return super.getDeletedAt();
    }

    @Override
    public String getDescription() {
        return super.getDescription();
    }

    @Override
    public String getImage() {
        return super.getImage();
    }

    @Override
    public void setCreatedAt(OffsetDateTime createdAt) {
        super.setCreatedAt(createdAt);
    }

    @Override
    public void setIdAgency(Long idAgency) {
        super.setIdAgency(idAgency);
    }

    @Override
    public void setAvailable(boolean available) {
        super.setAvailable(available);
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
    public void setDescription(String description) {
        super.setDescription(description);
    }

    @Override
    public void setId(Long id) {
        super.setId(id);
    }

    @Override
    public void setIdAgency(long idAgency) {
        super.setIdAgency(idAgency);
    }

    @Override
    public void setImage(String image) {
        super.setImage(image);
    }

    @Override
    public void setName(String name) {
        super.setName(name);
    }

    @Override
    public void setPositionProgressive(int positionProgressive) {
        super.setPositionProgressive(positionProgressive);
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
