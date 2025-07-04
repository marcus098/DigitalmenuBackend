package com.modules.webfluxmodule.models;

import com.modules.common.dto.*;
import com.modules.webfluxmodule.models.db.ComandReactive;

import java.util.List;
import java.util.Map;

public class ListToExport {
    private List<Object[]> categoriesList;
    private List<Object[]> ingredientsList;
    private List<Object[]> productsList;
    private List<ImageDto> imagesList;
    private List<Object[]> tablesList;
    private StyleDto styleDto;
    private List<ComandReactive> comands;

    public ListToExport(){

    }

    public ListToExport(List<CategoryDto> categoriesList, List<IngredientDto> ingredientsList, List<ProductDto> productsList, List<ImageDto> imagesList, List<TableDto> tablesList, StyleDto styleDto, List<ComandReactive> comands) {
        this.categoriesList = categoriesList.stream()
                .map(p -> new Object[]{p.getId(), p})
                .toList();

        this.productsList = productsList.stream()
                .map(p -> new Object[]{p.getId(), p})
                .toList();

        this.ingredientsList = ingredientsList.stream()
                .map(p -> new Object[]{p.getId(), p})
                .toList();

        this.imagesList = imagesList;
        this.tablesList = tablesList.stream()
                .map(p -> new Object[]{p.getId(), p})
                .toList();
        this.styleDto = styleDto;
        this.comands = comands;
    }

    public List<Object[]> getCategoriesList() {
        return categoriesList;
    }

    public List<ImageDto> getImagesList() {
        return imagesList;
    }

    public List<Object[]> getIngredientsList() {
        return ingredientsList;
    }

    public List<Object[]> getProductsList() {
        return productsList;
    }

    public List<Object[]> getTablesList() {
        return tablesList;
    }

    public StyleDto getStyleDto() {
        return styleDto;
    }

    public void setCategoriesList(List<Object[]> categoriesList) {
        this.categoriesList = categoriesList;
    }

    public void setImagesList(List<ImageDto> imagesList) {
        this.imagesList = imagesList;
    }

    public void setIngredientsList(List<Object[]> ingredientsList) {
        this.ingredientsList = ingredientsList;
    }

    public void setProductsList(List<Object[]> productsList) {
        this.productsList = productsList;
    }

    public void setStyleDto(StyleDto styleDto) {
        this.styleDto = styleDto;
    }

    public void setTablesList(List<Object[]> tablesList) {
        this.tablesList = tablesList;
    }

    public List<ComandReactive> getComands() {
        return comands;
    }

    public void setComands(List<ComandReactive> comands) {
        this.comands = comands;
    }

    @Override
    public String toString() {
        return "ListToExport{" +
                "categoriesList=" + (categoriesList != null ? categoriesList : "[]") +
                ", ingredientsList=" + (ingredientsList != null ? ingredientsList : "[]") +
                ", productsList=" + (productsList != null ? productsList : "[]") +
                ", imagesList=" + (imagesList != null ? imagesList : "[]") +
                ", tablesList=" + (tablesList != null ? tablesList : "[]") +
                ", styleDto=" + (styleDto != null ? styleDto : "null") +
                '}';
    }

}
