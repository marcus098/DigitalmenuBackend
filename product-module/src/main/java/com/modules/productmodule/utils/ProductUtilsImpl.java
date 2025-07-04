package com.modules.productmodule.utils;

import com.modules.common.dto.ProductDto;
import com.modules.common.finders.ProductUtils;
import com.modules.common.mapper.ProductMapper;
import com.modules.common.model.EntityLog;
import com.modules.common.utilities.Utilities;
import com.modules.productmodule.model.ProductJpa;
import com.modules.productmodule.repository.mongo.MongoProductRepository;
import com.modules.productmodule.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ProductUtilsImpl implements ProductUtils {
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private MongoProductRepository mongoProductRepository;
    @Autowired
    private Utilities utilities;
    @Autowired
    private ProductMapper productMapper;

    @Override
    public List<ProductDto> findAllByIdCategoryAndDeletedAndIdAgency(long idCategory, long idAgency) {
        return returnToDto(productRepository.findAllByIdCategoryAndDeletedAndIdAgency(idCategory, false, idAgency));
    }

    @Override
    public List<ProductDto> saveAllAndReturnValues(List<ProductDto> products, long idAgency) {
        try {
            return returnToDto(productRepository.saveAll(returnToJpa(products, idAgency)));
        } catch (Exception e) {
            e.printStackTrace(); // todo sistemare errore
            return new ArrayList<>();
        }
    }

    @Override
    public boolean saveAll(List<ProductDto> products, long idAgency) {
        productRepository.saveAll(returnToJpa(products, idAgency));
        return true;
    }

    @Override
    public boolean saveAllLogs(List<EntityLog<ProductDto>> products) {
        mongoProductRepository.saveAll(products);
        return true;
    }

    @Override
    public List<ProductDto> findProductsByConditions(List<Long> ids, long idAgency) {
        return returnToDto(productRepository.findProductsByConditions(ids, idAgency));
    }

    @Override
    public List<ProductDto> findAllByIdInAndIdAgencyAndDeleted(Set<Long> ids, long idAgency) {
        return returnToDto(productRepository.findAllByIdInAndIdAgencyAndDeleted(ids, idAgency, false));
    }

    @Override
    public List<ProductDto> findAllByIngredientIdAndDeleted(long id, long idAgency) {
        return returnToDto(productRepository.findAllByIngredientId(id, idAgency));
    }

    @Override
    public void updateImageToEmpty(String name) {
        productRepository.updateImageToEmpty(name);
    }

    private List<ProductDto> returnToDto(List<ProductJpa> prods) {
        return prods.stream()
                .map(p -> productMapper.toDto(p))
                .collect(Collectors.toList());
    }

    private List<ProductJpa> returnToJpa(List<ProductDto> prods, long idAgency){
        return prods
                .stream()
                .map(p -> new ProductJpa(
                        p.getId(),
                        p.getName(),
                        p.getDescription(),
                        utilities.convertOptionInProductToString(p.getOptions()),
                        p.getImage(),
                        p.getIdCategory(),
                        idAgency,
                        utilities.convertIntListToString(p.getTags()),
                        utilities.convertIntListToString(p.getAllergens()),
                        utilities.convertLongListToString(p.getIngredients()),
                        p.isAvailable(),
                        p.getPosition_progressive()
                ))
                .collect(Collectors.toList());
    }
}
