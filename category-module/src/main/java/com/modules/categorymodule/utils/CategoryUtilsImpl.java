package com.modules.categorymodule.utils;

import com.modules.categorymodule.repository.CategoryRepository;
import com.modules.common.dto.CategoryDto;
import com.modules.common.finders.CategoryUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CategoryUtilsImpl implements CategoryUtils {
    @Autowired
    private CategoryRepository categoryRepository;

    @Override
    public List<CategoryDto> findAllByIdInAndIdAgencyAndDeleted(Set<Long> ids, long idAgency) {
        return categoryRepository.findAllByIdInAndIdAgencyAndDeleted(ids, idAgency, false)
                .stream()
                .map(c -> new CategoryDto(c, new ArrayList<>()))
                .collect(Collectors.toList());
    }
}
