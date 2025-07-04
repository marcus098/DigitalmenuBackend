package com.modules.common.dto;

import com.fasterxml.jackson.annotation.JsonView;
import com.modules.common.model.OptionInProduct;
import com.modules.common.model.db.Product;

import java.util.List;

public class ProductDto {
    private Long id;
    private String name;
    private String description;
    private List<OptionInProduct> options;
    private String image;
    private Long idCategory;
    private List<Integer> tags;
    private List<Integer> allergens;
    private List<Long> ingredients;
    private boolean available;
    private int position_progressive;
    @JsonView(Views.Updating.class)
    private String sessionUpdating;
    @JsonView(Views.Updating.class)
    private String changeType;

    public ProductDto() {

    }

    public ProductDto(Long id, String name, String description, List<OptionInProduct> options, String image, Long idCategory, List<Integer> tags, List<Integer> allergens, List<Long> ingredients, boolean available, int position_progressive) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.options = options;
        this.image = image;
        this.idCategory = idCategory;
        this.tags = tags;
        this.allergens = allergens;
        this.ingredients = ingredients;
        this.available = available;
        this.position_progressive = position_progressive;
    }

    public ProductDto(String name, String description, List<OptionInProduct> options, String image, Long idCategory, List<Integer> tags, List<Integer> allergens, List<Long> ingredients, boolean available, int position_progressive) {
        this.name = name;
        this.description = description;
        this.options = options;
        this.image = image;
        this.idCategory = idCategory;
        this.tags = tags;
        this.allergens = allergens;
        this.ingredients = ingredients;
        this.available = available;
        this.position_progressive = position_progressive;
    }

    public ProductDto(Long id, String name, String description, List<OptionInProduct> options, String image, Long idCategory, List<Integer> tags, List<Integer> allergens, List<Long> ingredients, boolean available, int position_progressive, String sessionUpdating, String changeType) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.options = options;
        this.image = image;
        this.idCategory = idCategory;
        this.tags = tags;
        this.allergens = allergens;
        this.ingredients = ingredients;
        this.available = available;
        this.position_progressive = position_progressive;
        this.sessionUpdating = sessionUpdating;
        this.changeType = changeType;
    }

    public ProductDto(String name, String description, List<OptionInProduct> options, String image, Long idCategory, List<Integer> tags, List<Integer> allergens, List<Long> ingredients, boolean available, int position_progressive, String sessionUpdating, String changeType) {
        this.name = name;
        this.description = description;
        this.options = options;
        this.image = image;
        this.idCategory = idCategory;
        this.tags = tags;
        this.allergens = allergens;
        this.ingredients = ingredients;
        this.available = available;
        this.position_progressive = position_progressive;
        this.sessionUpdating = sessionUpdating;
        this.changeType = changeType;
    }

    public String getChangeType() {
        return changeType;
    }

    public String getSessionUpdating() {
        return sessionUpdating;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public void setSessionUpdating(String sessionUpdating) {
        this.sessionUpdating = sessionUpdating;
    }

    public int getPosition_progressive() {
        return position_progressive;
    }

    public void setPosition_progressive(int position_progressive) {
        this.position_progressive = position_progressive;
    }

    public List<Integer> getAllergens() {
        return allergens;
    }

    public Long getIdCategory() {
        return idCategory;
    }

    public String getDescription() {
        return description;
    }

    public List<Integer> getTags() {
        return tags;
    }

    public String getName() {
        return name;
    }

    public List<OptionInProduct> getOptions() {
        return options;
    }

    public List<Long> getIngredients() {
        return ingredients;
    }

    public String getImage() {
        return image;
    }

    public Long getId() {
        return id;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAllergens(List<Integer> allergens) {
        this.allergens = allergens;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void setOptions(List<OptionInProduct> options) {
        this.options = options;
    }

    public void setName(String name) {
        this.name = name;
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

    public void setTags(List<Integer> tags) {
        this.tags = tags;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "ProductDto{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", options=" + options +
                ", image='" + image + '\'' +
                ", idCategory=" + idCategory +
                ", tags=" + tags +
                ", allergens=" + allergens +
                ", ingredients=" + ingredients +
                ", available=" + available +
                ", position_progressive=" + position_progressive +
                ", sessionUpdating='" + sessionUpdating + '\'' +
                ", changeType='" + changeType + '\'' +
                '}';
    }
}
