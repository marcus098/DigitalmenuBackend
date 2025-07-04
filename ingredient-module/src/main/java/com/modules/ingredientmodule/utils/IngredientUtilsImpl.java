package com.modules.ingredientmodule.utils;

import com.modules.common.dto.IngredientDto;
import com.modules.common.finders.IngredientUtils;
import com.modules.common.mapper.IngredientMapper;
import com.modules.common.utilities.Utilities;
import com.modules.ingredientmodule.model.IngredientJpa;
import com.modules.ingredientmodule.repository.IngredientRepository;
import com.modules.ingredientmodule.repository.mongo.MongoIngredientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class IngredientUtilsImpl implements IngredientUtils {
    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private MongoIngredientRepository mongoIngredientRepository;
    @Autowired
    private Utilities utilities;
    @Autowired
    private IngredientMapper ingredientMapper;

    @Override
    public List<IngredientDto> findAllByIdIn(List<Long> ids) {
        return returnToDto(ingredientRepository.findAllByIdInAndDeleted(ids, false));
    }

    @Override
    public List<IngredientDto> findAllByIdInAndIdAgencyAndDeleted(Set<Long> ids, Long idAgency) {
        return returnToDto(ingredientRepository.findAllByIdInAndIdAgencyAndDeleted(ids, idAgency, false));
    }

    @Override
    public List<IngredientDto> findAllByIdInAndIdAgencyAndDeletedList(List<Long> ids, Long idAgency) {
        return returnToDto(ingredientRepository.findAllByIdInAndIdAgencyAndDeleted(ids, idAgency, false));
    }

    private List<IngredientDto> returnToDto(List<IngredientJpa> prods) {
        return prods.stream()
                .map(i -> ingredientMapper.toDto(i))
                .collect(Collectors.toList());
    }

    private List<IngredientJpa> returnToJpa(List<IngredientDto> ings, long idAgency){
        return ings
                .stream()
                .map(i -> new IngredientJpa(i.getId(), i.getName(), i.isAvailable(), i.isAddable(), i.isFrozen(), utilities.convertIntListToString(i.getAllergens()), idAgency, i.getPrice()))
                .collect(Collectors.toList());
    }
}
