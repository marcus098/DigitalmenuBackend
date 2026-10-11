package com.modules.common.mapper;

import com.modules.common.dto.CategoryDto;
import com.modules.common.dto.ProductDto;
import com.modules.common.model.LongInteger;
import com.modules.common.model.db.Product;
import com.modules.common.utilities.Utilities;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {
    @Autowired
    private Utilities utilities;

    public ProductDto toDto(Product product) {
        if (product == null) {
            return null;
        }

        ProductDto dto = new ProductDto();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setImage(product.getImage());
        dto.setAvailable(product.isAvailable());
        dto.setPosition_progressive(product.getPositionProgressive());
        // Solo gli allergeni dichiarati sul prodotto: eventuali id negativi legacy (copie degli
        // allergeni degli ingredienti) vengono scartati, il client li ricalcola dagli ingredienti
        dto.setAllergens(utilities.normalizeAllergens(utilities.convertStringToIntList(product.getAllergens())));
        dto.setTraces(utilities.normalizeAllergens(utilities.convertStringToIntList(product.getTraces())));
        dto.setIdCategory(product.getIdCategory());
        dto.setIngredients(utilities.convertStringToLongList(product.getIngredients()));
        dto.setTags(utilities.convertStringToIntList(product.getTags()));
        dto.setOptions(utilities.convertStringToOptionInProduct(product.getOptions()));

        return dto;
    }
}
