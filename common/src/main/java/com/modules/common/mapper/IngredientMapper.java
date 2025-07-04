package com.modules.common.mapper;

import com.modules.common.dto.IngredientDto;
import com.modules.common.model.db.Ingredient;
import com.modules.common.utilities.Utilities;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IngredientMapper {
    @Autowired
    private Utilities utilities;

    // Metodo che fa la conversione da Entità a DTO
    public IngredientDto toDto(Ingredient ingredient) {
        if (ingredient == null) {
            return null;
        }

        IngredientDto dto = new IngredientDto();
        dto.setId(ingredient.getId());
        dto.setName(ingredient.getName());
        dto.setPrice(ingredient.getPrice());
        dto.setAvailable(ingredient.isAvailable());
        dto.setAddable(ingredient.isAddable());
        dto.setFrozen(ingredient.isFrozen());
        
        // Usa il bean 'utilities' per la logica di conversione
        List<Integer> allergenList = utilities.convertStringToIntList(ingredient.getAllergens());
        dto.setAllergens(allergenList);

        return dto;
    }
}