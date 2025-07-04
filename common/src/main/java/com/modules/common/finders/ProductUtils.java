package com.modules.common.finders;

import com.modules.common.dto.ProductDto;
import com.modules.common.model.EntityLog;

import java.util.List;
import java.util.Set;

public interface ProductUtils {
    List<ProductDto> findAllByIdCategoryAndDeletedAndIdAgency(long idCategory, long idAgency);
    List<ProductDto> saveAllAndReturnValues(List<ProductDto> products, long idAgency);
    boolean saveAll(List<ProductDto> products, long idAgency);
    boolean saveAllLogs(List<EntityLog<ProductDto>> products);
    List<ProductDto> findProductsByConditions(List<Long> ids, long idAgency);
    List<ProductDto> findAllByIdInAndIdAgencyAndDeleted(Set<Long> ids, long idAgency);
    List<ProductDto> findAllByIngredientIdAndDeleted(long id, long idAgency);
    void updateImageToEmpty(String name);
}
