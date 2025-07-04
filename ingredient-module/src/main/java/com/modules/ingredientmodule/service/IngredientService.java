package com.modules.ingredientmodule.service;

import com.modules.common.dto.IngredientDto;
import com.modules.common.dto.ProductDto;
import com.modules.common.finders.ProductUtils;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.mapper.IngredientMapper;
import com.modules.common.model.EntityLog;
import com.modules.common.model.enums.LogOperation;
import com.modules.common.model.enums.TypeEntity;
import com.modules.common.utilities.Utilities;
import com.modules.ingredientmodule.model.IngredientJpa;
import com.modules.ingredientmodule.repository.IngredientRepository;
import com.modules.ingredientmodule.repository.mongo.MongoIngredientRepository;
import com.modules.ingredientmodule.requests.AddIngredient;
import com.modules.ingredientmodule.requests.UpdateIngredient;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class IngredientService {

    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private MongoIngredientRepository mongoIngredientRepository;
    @Autowired
    private ProductUtils productUtils;
    @Autowired
    private AuthenticatedUserProvider authUserProvider;
    @Autowired
    private Utilities utilities;
    @Autowired
    private IngredientMapper ingredientMapper;

    @Transactional
    public IngredientDto addIngredient(AddIngredient addIngredient) {
        try{
            long idAgency = authUserProvider.getAgencyId();
            long idUser = authUserProvider.getUserId();
            List<Integer> allergens = utilities.checkAllergens(addIngredient.getAllergens());
            IngredientJpa ingredient = ingredientRepository.save(new IngredientJpa(
                    addIngredient.getName(),
                    addIngredient.isAvailable(),
                    addIngredient.isAddable(),
                    addIngredient.isFrozen(),
                    utilities.convertIntListToString(allergens),
                    idAgency,
                    addIngredient.getPrice())
            );

            List<TypeEntity> entities = new ArrayList<>();
            entities.add(TypeEntity.INGREDIENT);

            IngredientDto ingredientDto = ingredientMapper.toDto(ingredient);

            mongoIngredientRepository.save(new EntityLog<IngredientDto>(LogOperation.ADD, null, ingredientDto, "addIngredient function", idUser, idAgency));

            //Utilities.updateType(entities);
            return ingredientDto;

        }catch (Exception e){
            return null;
        }
    }

    @Transactional
    public IngredientDto updateIngredient(UpdateIngredient updateIngredient) {
        try{
            long idAgency = authUserProvider.getAgencyId();
            long idUser = authUserProvider.getUserId();

            Optional<IngredientJpa> optionalIngredient = ingredientRepository.findByIdAndDeletedAndIdAgency(updateIngredient.getId(), false, idAgency);
            if (optionalIngredient.isEmpty()) {
                throw new EntityNotFoundException("Ingredient with id " + updateIngredient.getId() + " not found");
            }

            IngredientJpa ingredient = optionalIngredient.get();
            IngredientDto ingredientDto = ingredientMapper.toDto(ingredient);
            /*
                Devo controllare se gli allergeni sono stati modificati oppure no. Se si, devo modificare in cascata tutti i prodotti con questo ingrediente
             */

            List<Integer> oldAllergensList = utilities.convertStringToIntList(ingredient.getAllergens());
            List<Integer> newAllergensList = updateIngredient.getAllergens();

            boolean areEquals = (new HashSet<>(oldAllergensList)).equals(new HashSet<>(newAllergensList));

            List<EntityLog<ProductDto>> productLogs = new ArrayList<>();

            if(!areEquals){
                List<ProductDto> products = productUtils.findAllByIngredientIdAndDeleted(ingredient.getId(), idAgency);
                for (ProductDto product : products) {
                    /* Devo rimuovere i vecchi allergeni al prodotto e aggiungere quelli nuovi */
                    List<Integer> allergensInProduct = product.getAllergens();

                    // Rimuove ogni elemento di `olds` una sola volta da `allergensInProduct`
                    for (Integer oldAllergen : oldAllergensList) {
                        allergensInProduct.remove(oldAllergen);
                    }

                    // Aggiunge i nuovi allergeni
                    allergensInProduct.addAll(newAllergensList);
                    ProductDto old = product;

                    // Converte nuovamente in stringa e aggiorna il campo `allergens` del prodotto
                    product.setAllergens(allergensInProduct);

                    productLogs.add(new EntityLog<>(LogOperation.UPDATE, old, product, "from updateIngredient function", idUser, idAgency));
                }
                if(!products.isEmpty())
                    productUtils.saveAll(products, idAgency);
            }

            ingredient.setAddable(updateIngredient.isAddable());
            ingredient.setAvailable(updateIngredient.isAvailable());
            ingredient.setFrozen(updateIngredient.isFrozen());
            ingredient.setName(updateIngredient.getName());
            ingredient.setPrice(updateIngredient.getPrice());
            ingredient.setAllergens(utilities.convertIntListToString(newAllergensList));
            ingredientRepository.save(ingredient);

            mongoIngredientRepository.save(new EntityLog<>(LogOperation.UPDATE, ingredientDto, ingredientDto, "updateIngredient function", idUser, idAgency));

            if(!productLogs.isEmpty())
                productUtils.saveAllLogs(productLogs);

            List<TypeEntity> entities = new ArrayList<>();
            entities.add(TypeEntity.INGREDIENT);
            if(!areEquals){
                entities.add(TypeEntity.PRODUCT);
            }
            //Utilities.updateType(entities);
            return ingredientMapper.toDto(ingredient);
        }catch (Exception e){
            return null;
        }
    }

    @Transactional
    public List<Long> deleteIngredient(long id) {
        try{
            long idAgency = authUserProvider.getAgencyId();
            long idUser = authUserProvider.getUserId();

            Optional<IngredientJpa> optionalIngredient = ingredientRepository.findByIdAndDeletedAndIdAgency(id, false, idAgency);
            if (optionalIngredient.isEmpty()) {
                throw new EntityNotFoundException("Ingredient with id " + id + " not found");
            }
            IngredientJpa ingredient = optionalIngredient.get();
            ingredient.setDeleted(true);
            ingredient.setDeletedAt(OffsetDateTime.now());

            List<ProductDto> products = productUtils.findAllByIngredientIdAndDeleted(id, idAgency);
            List<EntityLog<ProductDto>> productLogs = new ArrayList<>();
            for (ProductDto product : products) {
                /* Devo rimuovere i vecchi allergeni al prodotto */
                List<Integer> allergensInProduct = product.getAllergens();
                List<Integer> olds = utilities.convertStringToIntList(ingredient.getAllergens()).stream().map(o -> -o).collect(Collectors.toList());

                // Rimuove ogni elemento di `olds` una sola volta da `allergensInProduct`
                for (Integer oldAllergen : olds) {
                    allergensInProduct.remove(oldAllergen);  // rimuove solo la prima occorrenza di `oldAllergen`
                }
                ProductDto pDto = new ProductDto(
                        product.getId(),
                        product.getName(),
                        product.getDescription(),
                        product.getOptions(),
                        product.getImage(),
                        product.getIdCategory(),
                        product.getTags(),
                        product.getAllergens(),
                        product.getIngredients(),
                        product.isAvailable(),
                        product.getPosition_progressive()
                );
                // Converte nuovamente in stringa e aggiorna il campo `allergens` del prodotto
                product.setAllergens(allergensInProduct);

                // Devo rimuovere l'ingrediente al prodotto
                List<Long> ingredientsInProduct = product.getIngredients();
                ingredientsInProduct.remove(id);
                product.setIngredients(ingredientsInProduct);
                productLogs.add(new EntityLog<>(LogOperation.UPDATE, pDto, product, "from deleteIngredient function", idUser, idAgency));
            }
            List<TypeEntity> entities = new ArrayList<>();
            entities.add(TypeEntity.INGREDIENT);

            if(!products.isEmpty()) {
                productUtils.saveAll(products, idAgency);
                entities.add(TypeEntity.PRODUCT);
            }

            ingredientRepository.save(ingredient);

            try {
                if(!productLogs.isEmpty()){
                    productUtils.saveAllLogs(productLogs);
                }

                mongoIngredientRepository.save(new EntityLog<>(LogOperation.DELETE, ingredientMapper.toDto(ingredient), null, "function deleteIngredient", idUser, idAgency));
            }catch (Exception e){
                ErrorLog.logger.error("Errore salvataggio log dopo eliminazione ingrediente con id " + id + ": ", e);
            }
            //utilities.updateType(entities);
            return products.stream().mapToLong(ProductDto::getId).boxed().collect(Collectors.toList());
        }catch (Exception e){
            ErrorLog.logger.error("Errore eliminazione ingrediente con id " + id + ": ", e);
            return null;
        }
    }

    @Transactional
    public int setAvailable(long idCategory, boolean available){
        try{
            long idAgency = authUserProvider.getAgencyId();
            long idUser = authUserProvider.getUserId();

            Optional<IngredientJpa> optionalIngredient = ingredientRepository.findByIdAndDeletedAndIdAgency(idCategory, false, idAgency);
            if (optionalIngredient.isEmpty()) {
                ErrorLog.logger.error("ingredient not found");
                return 404;
            }
            IngredientJpa ingredient = optionalIngredient.get();
            IngredientDto ingredientDto = ingredientMapper.toDto(ingredient);
            ingredient.setAvailable(available);
            ingredientRepository.save(ingredient);
            mongoIngredientRepository.save(new EntityLog<>(LogOperation.OTHER, ingredientDto, ingredientDto, "function setAvailable value " + available, idUser, idAgency));
            return 200;
        }catch (Exception e){
            ErrorLog.logger.error("Errore setAvailable ingredienti", e);
            return 400;
        }
    }

    @Transactional
    public int setAddable(long idCategory, boolean addable){
        try{
            long idAgency = authUserProvider.getAgencyId();
            long idUser = authUserProvider.getUserId();

            Optional<IngredientJpa> optionalIngredient = ingredientRepository.findByIdAndDeletedAndIdAgency(idCategory, false, idAgency);
            if (optionalIngredient.isEmpty()) {
                ErrorLog.logger.error("ingredient not found");
                return 404;
            }
            IngredientJpa ingredient = optionalIngredient.get();
            IngredientDto ingredientDto = ingredientMapper.toDto(ingredient);
            ingredient.setAddable(addable);
            ingredientRepository.save(ingredient);
            mongoIngredientRepository.save(new EntityLog<>(LogOperation.OTHER, ingredientDto, ingredientDto, "function setAddable value " + addable, idUser, idAgency));
            return 200;
        }catch (Exception e){
            ErrorLog.logger.error("Errore setAddable ingredienti", e);
            return 400;
        }
    }
}
