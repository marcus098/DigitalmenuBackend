package com.modules.productmodule.requests;

import com.modules.common.model.Request;

import java.util.ArrayList;
import java.util.List;

public class AddProduct implements Request {
    private String name;
    private String description;
    //private OptionInProductList options;
    private String image;
    private Long idCategory;
    private List<Integer> tags = new ArrayList<>();
    private List<Integer> allergens = new ArrayList<>();
    private List<Long> ingredients = new ArrayList<>();
    private boolean available;

    public AddProduct() {}

    public List<Integer> getTags() {
        return tags;
    }

    public List<Long> getIngredients() {
        return ingredients;
    }

    public String getDescription() {
        return description;
    }

    public boolean isAvailable() {
        return available;
    }

    public Long getIdCategory() {
        return idCategory;
    }

    public String getImage() {
        return image;
    }

    //public OptionInProductList getOptions() {
    //    return options;
    //}
//
    //public void setOptions(OptionInProductList options) {
    //    this.options = options;
    //}

    public List<Integer> getAllergens() {
        return allergens;
    }

    public String getName() {
        return name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setIdCategory(Long idCategory) {
        this.idCategory = idCategory;
    }

    public void setIngredients(List<Long> ingredients) {
        this.ingredients = ingredients;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public void setTags(List<Integer> tags) {
        this.tags = tags;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void setAllergens(List<Integer> allergens) {
        this.allergens = allergens;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "AddProduct{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                //", options=" + options +
                ", image='" + image + '\'' +
                ", idCategory=" + idCategory +
                ", tags=" + tags +
                ", allergens=" + allergens +
                ", ingredients=" + ingredients +
                ", available=" + available +
                '}';
    }

    @Override
    public boolean validate() {
        return name != null && !name.trim().isEmpty() /*&& options != null && !options.getOptionInProductList().isEmpty() */&& idCategory > 0;
    }
}
