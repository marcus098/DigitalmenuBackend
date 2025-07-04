package com.modules.common.mapper;

import com.modules.common.dto.CategoryDto;
import com.modules.common.model.LongInteger;
import com.modules.common.model.db.Category;
import com.modules.common.utilities.Utilities;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class CategoryMapper {

    public CategoryDto toDto(Category category, List<LongInteger> products) {
        if (category == null) {
            return null;
        }

        CategoryDto dto = new CategoryDto();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setDescription(category.getDescription());
        dto.setImage(category.getImage());
        dto.setAvailable(category.isAvailable());
        dto.setProducts(products);
        dto.setProductsLong(products.stream().map(LongInteger::getLongValue).collect(Collectors.toList()));
        dto.setProgressiveNumber(category.getPositionProgressive());

        return dto;
    }
}
