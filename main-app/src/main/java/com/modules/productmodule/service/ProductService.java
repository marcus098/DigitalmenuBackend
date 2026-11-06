package com.modules.productmodule.service;

import com.modules.servletconfiguration.kafka.AvailabilityUpdateProducer;
import com.modules.common.dto.IngredientDto;
import com.modules.common.dto.ProductDto;
import com.modules.common.finders.FileUtils;
import com.modules.common.finders.IngredientUtils;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.mapper.ProductMapper;
import com.modules.common.model.EntityLog;
import com.modules.common.model.OptionInProduct;
import com.modules.common.model.enums.LogOperation;
import com.modules.common.model.variant.VariantGroup;
import com.modules.common.model.variant.VariantGroups;
import com.modules.common.utilities.Utilities;
import com.modules.productmodule.model.ProductJpa;
import com.modules.productmodule.repository.mongo.MongoProductRepository;
import com.modules.productmodule.repository.ProductRepository;
import com.modules.common.model.IdWithOrder;
import com.modules.productmodule.requests.AddProduct;
import com.modules.productmodule.requests.UpdateProduct;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProductService {

    @Autowired(required = false)
    private AvailabilityUpdateProducer availabilityUpdateProducer;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private MongoProductRepository mongoProductRepository;
    @Autowired
    private IngredientUtils ingredientFinder;
    @Autowired
    private FileUtils fileUtils;
    @Autowired
    private AuthenticatedUserProvider authUserProvider;
    @Autowired
    private Utilities utilities;
    @Autowired
    private ProductMapper productMapper;

    @Transactional
    public ProductDto addProduct(AddProduct addProduct, MultipartFile file, List<OptionInProduct> options) {
        return addProduct(addProduct, file, options, null);
    }

    /** @param variantGroups gruppi di varianti già validati (null o vuoto = nessun gruppo) */
    @Transactional
    public ProductDto addProduct(AddProduct addProduct, MultipartFile file, List<OptionInProduct> options, List<VariantGroup> variantGroups) {
        try {
            long idUser = authUserProvider.getUserId();
            long idAgency = authUserProvider.getAgencyId();

            Integer progressiveNumber = productRepository.findMaxPositionByIdAgencyAndIdCategory(idAgency, addProduct.getIdCategory());
            if (progressiveNumber == null) {
                progressiveNumber = 1;
            } else {
                progressiveNumber++;
            }

            /*
                Sul prodotto salvo solo gli allergeni dichiarati direttamente e le tracce.
                Gli allergeni degli ingredienti non vengono più copiati: quelli effettivi
                (prodotto ∪ ingredienti) li calcola il client, così restano sempre allineati.
             */
            List<Integer> allergensList = utilities.normalizeAllergens(addProduct.getAllergens());
            List<Integer> tracesList = utilities.normalizeAllergens(addProduct.getTraces());
            if(file != null)
                addProduct.setImage(fileUtils.uploadImageWithBucket(file, idAgency, idUser, "img"));
            ProductJpa newProduct = new ProductJpa(
                    addProduct.getName(),
                    addProduct.getDescription(),
                    utilities.convertOptionInProductToString(options),
                    addProduct.getImage(),
                    addProduct.getIdCategory(),
                    idAgency,
                    utilities.convertIntListToString(addProduct.getTags()),
                    utilities.convertIntListToString(allergensList),
                    utilities.convertLongListToString(addProduct.getIngredients()),
                    addProduct.isAvailable(),
                    progressiveNumber
            );
            newProduct.setTraces(utilities.convertIntListToString(tracesList));
            newProduct.setVariantGroups(VariantGroups.toJson(variantGroups));
            ProductJpa product = productRepository.save(newProduct);

            EntityLog<ProductDto> productLog = new EntityLog<>(LogOperation.OTHER, null, productMapper.toDto(product), "function addProduct", idUser, idAgency);
            mongoProductRepository.save(productLog);

            return productMapper.toDto(product);

        } catch (Exception e) {
            ErrorLog.logger.error("Errore addProduct", e);
            return null;
        }
    }

    @Transactional
    public ProductDto updateProduct(UpdateProduct updateProduct, MultipartFile file, List<OptionInProduct> options) {
        return updateProduct(updateProduct, file, options, null);
    }

    /** @param variantGroups gruppi di varianti già validati; null = invariati, lista vuota = rimuovi tutti */
    @Transactional
    public ProductDto updateProduct(UpdateProduct updateProduct, MultipartFile file, List<OptionInProduct> options, List<VariantGroup> variantGroups) {
        try {
            long idUser = authUserProvider.getUserId();
            long idAgency = authUserProvider.getAgencyId();

            Optional<ProductJpa> optionalProduct = productRepository.findByIdAndIdAgencyAndDeleted(updateProduct.getId(), idAgency, false);

            if (optionalProduct.isEmpty()) {
                throw new EntityNotFoundException("Product with id " + updateProduct.getId() + " not found");
            }

            if(file != null)
                updateProduct.setImage(fileUtils.uploadImageWithBucket(file, idAgency, idUser, "img"));

            ProductJpa product = optionalProduct.get();
            ProductDto oldProductDto = productMapper.toDto(product);

            // Solo allergeni dichiarati sul prodotto (vedi addProduct)
            List<Integer> allergensList = utilities.normalizeAllergens(updateProduct.getAllergens());
            List<IngredientDto> ingredients = ingredientFinder.findAllByIdInAndIdAgencyAndDeletedList(updateProduct.getIngredients(), idAgency);
            List<Long> newProductIngredientsId = new ArrayList<>();

            for (IngredientDto ingredient : ingredients) {
                newProductIngredientsId.add(ingredient.getId());
            }

            product.setName(updateProduct.getName());
            product.setDescription(updateProduct.getDescription());
            product.setOptions(utilities.convertOptionInProductToString(options));
            product.setImage(updateProduct.getImage());
            product.setIngredients(utilities.convertLongListToString(newProductIngredientsId));
            product.setTags(utilities.convertIntListToString(updateProduct.getTags()));
            product.setAllergens(utilities.convertIntListToString(allergensList));
            // Tracce non inviate (client non aggiornato): mantengo quelle esistenti
            if (updateProduct.getTraces() != null)
                product.setTraces(utilities.convertIntListToString(utilities.normalizeAllergens(updateProduct.getTraces())));
            // Gruppi di varianti non inviati (client non aggiornato): mantengo quelli esistenti
            if (variantGroups != null)
                product.setVariantGroups(VariantGroups.toJson(variantGroups));
            productRepository.save(product);

            EntityLog productLog = new EntityLog<>(LogOperation.OTHER, oldProductDto, productMapper.toDto(product), "function updateProduct", idUser, idAgency);
            mongoProductRepository.save(productLog);

            return productMapper.toDto(product);
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public int changeOrder(List<IdWithOrder> list) {
        try {
            long idUser = authUserProvider.getUserId();
            long idAgency = authUserProvider.getAgencyId();

            Set<Long> ids = list.stream().map(IdWithOrder::getId).collect(Collectors.toSet());
            List<ProductJpa> products = productRepository.findAllByIdInAndIdAgencyAndDeleted(ids, idAgency, false);
            if (products.isEmpty()) {
                return 404;
            }

            Map<Long, Long> newOrderMap = list.stream()
                    .collect(Collectors.toMap(IdWithOrder::getId, IdWithOrder::getOrder));

            for (ProductJpa product : products) {
                if (newOrderMap.containsKey(product.getId())) {
                    product.setPositionProgressive(newOrderMap.get(product.getId()).intValue());
                }
            }

            productRepository.saveAll(products);

            EntityLog<ProductDto> productLog = new EntityLog<>(LogOperation.OTHER, null, null, "function changeOrder " + list, idUser, idAgency);
            mongoProductRepository.save(productLog);

            return 200;
        } catch (Exception e) {
            ErrorLog.logger.error("Errore changeOrder product", e);
            return 400;
        }
    }

    @Transactional
    public int deleteProduct(long idProduct) {
        try {
            long idUser = authUserProvider.getUserId();
            long idAgency = authUserProvider.getAgencyId();

            Optional<ProductJpa> optionalProduct = productRepository.findByIdAndIdAgencyAndDeleted(idProduct, idAgency, false);
            if (optionalProduct.isEmpty()) {
                throw new EntityNotFoundException("Product with id " + idProduct + "not found");
            }
            ProductJpa product = optionalProduct.get();
            product.setDeleted(true);
            product.setDeletedAt(OffsetDateTime.now());
            productRepository.save(product);

            EntityLog<ProductDto> productLog = new EntityLog<>(LogOperation.DELETE, productMapper.toDto(product), null, "function deleteProduct", idUser, idAgency);
            mongoProductRepository.save(productLog);

            return 200;
        } catch (Exception e) {
            return 403;
        }
    }

    @Transactional
    public int setAvailable(long idProduct, boolean available) {
        try {
            long idUser = authUserProvider.getUserId();
            long idAgency = authUserProvider.getAgencyId();

            Optional<ProductJpa> product = productRepository.findByIdAndIdAgencyAndDeleted(idProduct, idAgency, false);
            if (product.isEmpty()) {
                throw new EntityNotFoundException("Product with id " + idProduct + "not found");
            }
            ProductJpa product1 = product.get();
            ProductDto oldProductDto = productMapper.toDto(product1);

            product1.setAvailable(available);
            productRepository.save(product1);
            if (availabilityUpdateProducer != null) availabilityUpdateProducer.send(idAgency, AvailabilityUpdateProducer.Kind.PRODUCT, idProduct, available);

            EntityLog<ProductDto> productLog = new EntityLog<>(LogOperation.OTHER, oldProductDto, productMapper.toDto(product1), "function setAvailable, value " + available, idUser, idAgency);
            mongoProductRepository.save(productLog);
            return 200;
        } catch (Exception e) {
            ErrorLog.logger.error("Errore setAvailable product ", e);
            return 400;
        }
    }


    public enum VariantAvailability { OK, PRODUCT_NOT_FOUND, CHOICE_NOT_FOUND, ERROR }

    /**
     * Esaurisce/ripristina una scelta di un gruppo di varianti. Pubblica l'evento "disponibilità" del prodotto
     * (kind PRODUCT, con groupId/choiceId/choiceAvailable in più) così menu cliente e app staff si aggiornano.
     */
    @Transactional
    public VariantAvailability setVariantAvailable(long idProduct, String groupId, String choiceId, boolean available) {
        try {
            long idUser = authUserProvider.getUserId();
            long idAgency = authUserProvider.getAgencyId();

            Optional<ProductJpa> optional = productRepository.findByIdAndIdAgencyAndDeleted(idProduct, idAgency, false);
            if (optional.isEmpty()) return VariantAvailability.PRODUCT_NOT_FOUND;
            ProductJpa product = optional.get();
            ProductDto oldProductDto = productMapper.toDto(product);

            List<VariantGroup> groups = VariantGroups.parse(product.getVariantGroups());
            if (!VariantGroups.setChoiceAvailable(groups, groupId, choiceId, available)) return VariantAvailability.CHOICE_NOT_FOUND;
            product.setVariantGroups(VariantGroups.toJson(groups));
            productRepository.save(product);
            if (availabilityUpdateProducer != null) {
                availabilityUpdateProducer.sendVariant(idAgency, idProduct, product.isAvailable(), groupId, choiceId, available);
            }

            EntityLog<ProductDto> productLog = new EntityLog<>(LogOperation.OTHER, oldProductDto, productMapper.toDto(product),
                    "function setVariantAvailable " + groupId + "/" + choiceId + ", value " + available, idUser, idAgency);
            mongoProductRepository.save(productLog);
            return VariantAvailability.OK;
        } catch (Exception e) {
            ErrorLog.logger.error("Errore setVariantAvailable product ", e);
            return VariantAvailability.ERROR;
        }
    }

}
