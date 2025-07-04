package com.modules.common.finders;

import com.modules.common.dto.CategoryDto;

import java.util.List;
import java.util.Set;

public interface CategoryUtils {
    List<CategoryDto> findAllByIdInAndIdAgencyAndDeleted(Set<Long> ids, long idAgency);
}
