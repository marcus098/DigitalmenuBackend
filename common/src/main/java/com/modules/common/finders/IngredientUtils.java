package com.modules.common.finders;

import com.modules.common.dto.IngredientDto;

import java.util.List;
import java.util.Set;

public interface IngredientUtils {
    List<IngredientDto> findAllByIdIn(List<Long> ids);
    List<IngredientDto> findAllByIdInAndIdAgencyAndDeleted(Set<Long> ids, Long idAgency);
    List<IngredientDto> findAllByIdInAndIdAgencyAndDeletedList(List<Long> ids, Long idAgency);
}
