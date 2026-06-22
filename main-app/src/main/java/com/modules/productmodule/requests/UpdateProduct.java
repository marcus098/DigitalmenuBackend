package com.modules.productmodule.requests;

import com.modules.common.model.Request;

import java.util.ArrayList;
import java.util.List;

public class UpdateProduct implements Request {
    private Long id;
    private String name;
    private String description;
    private String image;
    private Long idCategory;
    private Long idAgency;
    private List<Integer> tags = new ArrayList<>();
    private List<Integer> allergens = new ArrayList<>();
    private List<Long> ingredients = new ArrayList<>();
    private boolean available;
    private int positionProgressive;

    public UpdateProduct() {}

    public List<Integer> getAllergens() {
        return allergens;
    }

    public String getName() {
        return name;
    }

    public int getPositionProgressive() {
        return positionProgressive;
    }

    public Long getId() {
        return id;
    }

    public Long getIdCategory() {
        return idCategory;
    }

    public Long getIdAgency() {
        return idAgency;
    }

    public String getImage() {
        return image;
    }

    public String getDescription() {
        return description;
    }

    public List<Integer> getTags() {
        return tags;
    }

    public List<Long> getIngredients() {
        return ingredients;
    }

    public void setAllergens(List<Integer> allergens) {
        this.allergens = allergens;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPositionProgressive(int positionProgressive) {
        this.positionProgressive = positionProgressive;
    }

    public void setIdAgency(Long idAgency) {
        this.idAgency = idAgency;
    }

    public void setTags(List<Integer> tags) {
        this.tags = tags;
    }

    public void setIngredients(List<Long> ingredients) {
        this.ingredients = ingredients;
    }

    public void setIdCategory(Long idCategory) {
        this.idCategory = idCategory;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public boolean isAvailable() {
        return available;
    }

    @Override
    public String toString() {
        return "UpdateProduct{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", image='" + image + '\'' +
                ", idCategory=" + idCategory +
                ", idAgency=" + idAgency +
                ", tags=" + tags +
                ", allergens=" + allergens +
                ", ingredients=" + ingredients +
                ", available=" + available +
                ", positionProgressive=" + positionProgressive +
                '}';
    }

    @Override
    public boolean validate() {
        return id>0 && name != null && !name.trim().isEmpty() && /*!options.isEmpty() &&*/ idCategory > 0 && positionProgressive > 0;
    }
}
