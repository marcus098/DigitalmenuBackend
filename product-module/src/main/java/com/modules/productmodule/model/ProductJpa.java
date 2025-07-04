package com.modules.productmodule.model;

import com.modules.common.dto.ProductDto;
import com.modules.common.model.OptionInProduct;
import com.modules.common.model.OptionInProductList;
import com.modules.common.model.db.Product;
import com.modules.common.utilities.Utilities;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Table(name = "products")
public class ProductJpa extends Product {
    public ProductJpa() {
        super();
    }

    public ProductJpa(Long id, String name, String description, String options, String image, Long idCategory, Long idAgency, String tags, String allergens, String ingredients, boolean available, int positionProgressive) {
        super(id, name, description, options, image, idCategory, idAgency, tags, allergens, ingredients, available, positionProgressive);
    }

    public ProductJpa(String name, String description, String options, String image, Long idCategory, Long idAgency, String tags, String allergens, String ingredients, boolean available, int positionProgressive) {
        super(name, description, options, image, idCategory, idAgency, tags, allergens, ingredients, available, positionProgressive);
    }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Override
    public Long getId() {
        return super.getId();
    }

    @Column(nullable = false, name = "id_agency")
    @Override
    public Long getIdAgency() {
        return super.getIdAgency();
    }

    @Column(nullable = false)
    @Override
    public String getName() {
        return super.getName();
    }

    @Column(nullable = false)
    @Override
    public String getOptions() {
        return super.getOptions();
    }

    @Column(nullable = false, name = "id_category")
    @Override
    public Long getIdCategory() {
        return super.getIdCategory();
    }

    @Column(nullable = false)
    @Override
    public boolean isAvailable() {
        return super.isAvailable();
    }

    @Column(nullable = false, name = "created_at")
    @Override
    public OffsetDateTime getCreatedAt() {
        return super.getCreatedAt();
    }

    @Column(nullable = false, name = "position_progressive")
    @Override
    public int getPositionProgressive() {
        return super.getPositionProgressive();
    }

    @Override
    public boolean isDeleted() {
        return super.isDeleted();
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
    @Column(name="deleted_at")
    public OffsetDateTime getDeletedAt() {
        return super.getDeletedAt();
    }

    @Override
    public void setPositionProgressive(int positionProgressive) {
        super.setPositionProgressive(positionProgressive);
    }

    @Override
    public void setDeletedAt(OffsetDateTime deletedAt) {
        super.setDeletedAt(deletedAt);
    }

    @Override
    public void setDeleted(boolean deleted) {
        super.setDeleted(deleted);
    }

    @Override
    public void setCreatedAt(OffsetDateTime createdAt) {
        super.setCreatedAt(createdAt);
    }

    @Override
    public String getAllergens() {
        return super.getAllergens();
    }

    @Override
    public String getIngredients() {
        return super.getIngredients();
    }

    @Override
    public String getTags() {
        return super.getTags();
    }

    @Override
    public void setImage(String image) {
        super.setImage(image);
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
    public void setAvailable(boolean available) {
        super.setAvailable(available);
    }

    @Override
    public void setName(String name) {
        super.setName(name);
    }

    @Override
    public void setIdAgency(Long idAgency) {
        super.setIdAgency(idAgency);
    }

    @Override
    public void setIdCategory(Long idCategory) {
        super.setIdCategory(idCategory);
    }

    @Override
    public void setOptions(String options) {
        super.setOptions(options);
    }

    @Override
    public void setAllergens(String allergens) {
        super.setAllergens(allergens);
    }

    @Override
    public void setIngredients(String ingredients) {
        super.setIngredients(ingredients);
    }

    @Override
    public void setTags(String tags) {
        super.setTags(tags);
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
