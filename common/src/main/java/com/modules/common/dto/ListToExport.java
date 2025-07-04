package com.modules.common.dto;

import com.modules.common.model.Comand;
import lombok.Data;

import java.util.List;


@Data
public class ListToExport {
    private List<CategoryDto> categoriesList;
    private List<IngredientDto> ingredientsList;
    private List<ProductDto> productsList;
    private List<ImageDto> imagesList;
    private List<TableDto> tablesList;
    private StyleDto styleDto;
    private List<Comand> comands;

    public ListToExport(){

    }

    public ListToExport(List<CategoryDto> categoriesList, List<IngredientDto> ingredientsList, List<ProductDto> productsList, List<ImageDto> imagesList, List<TableDto> tablesList, StyleDto styleDto) {
        this.categoriesList = categoriesList;
        this.ingredientsList = ingredientsList;
        this.productsList = productsList;
        this.imagesList = imagesList;
        this.tablesList = tablesList;
        this.styleDto = styleDto;
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
