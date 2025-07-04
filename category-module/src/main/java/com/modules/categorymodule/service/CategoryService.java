package com.modules.categorymodule.service;

import com.modules.categorymodule.model.CategoryJpa;
import com.modules.categorymodule.model.CategoryLog;
import com.modules.categorymodule.repository.CategoryRepository;
import com.modules.categorymodule.repository.mongo.MongoCategoryRepository;
import com.modules.categorymodule.requests.AddCategory;
import com.modules.categorymodule.requests.UpdateCategory;
import com.modules.common.dto.CategoryDto;
import com.modules.common.dto.ProductDto;
import com.modules.common.finders.FileUtils;
import com.modules.common.finders.ProductUtils;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.EntityLog;
import com.modules.common.model.IdWithOrder;
import com.modules.common.model.LongInteger;
import com.modules.common.model.enums.LogOperation;
import com.modules.common.model.enums.TypeEntity;
import com.modules.servletconfiguration.exceptions.UnauthorizedException;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CategoryService {
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private MongoCategoryRepository mongoCategoryRepository;
    @Autowired
    private AuthenticatedUserProvider authUserProvider;
    @Autowired
    private FileUtils fileUtils;
    @Autowired
    private ProductUtils productUtils;

    /**
     * Recupera il progressivo piu' grande presente nel db cercando per azienda e crea una nuova categoria con progressivo = maggiore + 1
     * Ritorna CategoryDto con le informazioni utili al frontend
     * Devo impostare idCategory = id ai nuovi prodotti presenti nella categoria
     *
     * @param addCategory
     * @return CategoryDto
     */
    @Transactional
    public CategoryDto addCategory(AddCategory addCategory, MultipartFile file) throws UnauthorizedException, Exception {
        long idUser = authUserProvider.getUserId();
        long idAgency = authUserProvider.getAgencyId();

        Integer progressive = categoryRepository.findMaxPositionByAgency(idAgency);
        if (file != null)
            addCategory.setImage(fileUtils.uploadImageWithBucket(file, idAgency, idUser, "img"));
        CategoryJpa category = categoryRepository.save(new CategoryJpa(addCategory, idAgency, progressive == null ? 1 : progressive + 1));

        List<ProductDto> products = productUtils.findAllByIdCategoryAndDeletedAndIdAgency(category.getId(), idAgency);

        List<EntityLog<ProductDto>> productLogs = new ArrayList<>();
        for (ProductDto productDto : products) {
            ProductDto oldValue = productDto;
            productDto.setIdCategory(category.getId());
            productLogs.add(new EntityLog<>(LogOperation.OTHER, oldValue, productDto, "from function addCategory", idUser, idAgency));
        }

        if(!productUtils.saveAll(products, idAgency))
            throw new Exception("Errore salvataggio prodotti");

        List<TypeEntity> entities = new ArrayList<>();
        entities.add(TypeEntity.CATEGORY);
        if (!products.isEmpty()) {
            entities.add(TypeEntity.PRODUCT);
        }
        //Utilities.updateType(entities);

        CategoryDto categoryDto = new CategoryDto(category, products.stream().map(ProductDto::getId).collect(Collectors.toList()));

        CategoryLog categoryLog = new CategoryLog(LogOperation.ADD, categoryDto, "function addCategory", idUser, idAgency, true);
        mongoCategoryRepository.save(categoryLog);

        if (!productLogs.isEmpty())
            productUtils.saveAllLogs(productLogs); // todo controllare se ha salvato i log
        return categoryDto;
    }

    /**
     * Modifico tutti i campi della categoria lasciando invariato il progressivo
     * per modificare anche l'ordinamento devo richiamare changeOrder
     * Cerco su db passando id categoria, id azienda e deleted a false
     * Devo impostare idCategory ai prodotti rimossi dalla categoria a -1
     * Devo impostare idCategory = id ai nuovi prodotti presenti nella categoria
     *
     * @param updateCategory
     * @return
     */
    @Transactional
    public CategoryDto updateCategory(UpdateCategory updateCategory, MultipartFile file) throws UnauthorizedException, Exception {
        long idUser = authUserProvider.getUserId();
        long idAgency = authUserProvider.getAgencyId();

        CategoryJpa category = categoryRepository.findByIdAndIdAgencyAndDeleted(updateCategory.getId(), idAgency, false).orElseThrow();

        if (file != null)
            updateCategory.setImage(fileUtils.uploadImageWithBucket(file, idAgency, idUser, "img"));

        List<LongInteger> listLongInteger = updateCategory.getProducts().stream().map((p) -> {
            LongInteger longInteger = new LongInteger();
            String[] splitted = p.split("\\|");
            if (splitted.length == 2) {
                longInteger.setLongValue(Long.parseLong(splitted[0]));
                longInteger.setIntValue(Integer.parseInt(splitted[1]));
            }
            return longInteger;
        }).collect(Collectors.toList());

        List<ProductDto> oldProducts = productUtils.findAllByIdCategoryAndDeletedAndIdAgency(category.getId(), idAgency);
        List<ProductDto> newProducts = productUtils.findProductsByConditions(listLongInteger.stream().map(LongInteger::getLongValue).collect(Collectors.toList()), idAgency);

        Set<Long> oldProductsIds = oldProducts.stream().map(ProductDto::getId).collect(Collectors.toSet());
        Set<Long> newProductsIds = newProducts.stream().map(ProductDto::getId).collect(Collectors.toSet());

        boolean areEqual = oldProductsIds.equals(newProductsIds);

        category.setName(updateCategory.getName());
        category.setDescription(updateCategory.getDescription());
        category.setAvailable(updateCategory.isAvailable());
        category.setImage(updateCategory.getImage());
        category = categoryRepository.save(category);
        // todo log

        List<Long> productsId = new ArrayList<>();

        if (!areEqual) {
            List<ProductDto> productsToSave = new ArrayList<>();

            // 1. Gestisci i vecchi prodotti: Se un prodotto vecchio non è nella nuova lista, impostiamo `idCategory = -1L`
            for (ProductDto product : oldProducts) {
                if (!newProductsIds.contains(product.getId())) {
                    product.setIdCategory(-1L);
                    product.setPosition_progressive(0); // o un altro valore se necessario
                } else {
                    // Se il prodotto è sia in oldProducts che in newProducts, impostiamo `idCategory` alla categoria corrente
                    product.setIdCategory(category.getId());

                    // Troviamo la `positionProgressive` dalla nuova lista
                    Optional<LongInteger> optionalLongInteger = listLongInteger.stream()
                            .filter(longInteger -> longInteger.getLongValue() == product.getId())
                            .findFirst();
                    product.setPosition_progressive(optionalLongInteger.map(LongInteger::getIntValue).orElse(0));
                }
                productsToSave.add(product);
            }

            // 2. Gestisci i nuovi prodotti: Aggiungi quelli che non sono già presenti nella lista dei vecchi
            for (ProductDto product : newProducts) {
                productsId.add(product.getId());
                if (!oldProductsIds.contains(product.getId())) {
                    // Imposta `idCategory` alla categoria corrente per i nuovi prodotti
                    product.setIdCategory(category.getId());

                    // Troviamo la `positionProgressive` dalla nuova lista
                    Optional<LongInteger> optionalLongInteger = listLongInteger.stream()
                            .filter(longInteger -> longInteger.getLongValue() == product.getId())
                            .findFirst();
                    product.setPosition_progressive(optionalLongInteger.map(LongInteger::getIntValue).orElse(0));

                    productsToSave.add(product);
                }
            }

            // Salva tutte le modifiche ai prodotti
            if (!productsToSave.isEmpty()) {
                productUtils.saveAll(productsToSave, idAgency);
            }
        }

        List<TypeEntity> entities = new ArrayList<>();
        entities.add(TypeEntity.CATEGORY);

        if (!areEqual) {
            entities.add(TypeEntity.PRODUCT);
        }

        //Utilities.updateType(entities);
        return new CategoryDto(category, productsId);

    }

    /**
     * Oltre a impostare la categoria come eliminata su db, devo impostare tutti i prodotti con idCategory = -1 (senza categoria)
     *
     * @param id
     * @return
     */
    @Transactional
    public int deleteCategory(long id) throws UnauthorizedException, Exception {
        long idUser = authUserProvider.getUserId();
        long idAgency = authUserProvider.getAgencyId();

        Optional<CategoryJpa> categoryOptional = categoryRepository.findByIdAndIdAgencyAndDeleted(id, idAgency, false);
        if (categoryOptional.isEmpty()) {
            return 404; // Categoria non trovata
        }

        CategoryJpa category = categoryOptional.get();
        int positionToDelete = category.getPositionProgressive(); // Recupera la posizione progressiva della categoria da eliminare

        // Elimina la categoria
        category.setDeleted(true);
        category.setDeletedAt(OffsetDateTime.now());
        categoryRepository.save(category);

        // Decrementa le posizioni delle categorie che si trovano dopo quella eliminata
        categoryRepository.decrementPositions(idAgency, positionToDelete + 1, Integer.MAX_VALUE); // Decrementa posizioni da quella successiva

        List<ProductDto> products = productUtils.findAllByIdCategoryAndDeletedAndIdAgency(id, idAgency);
        for (ProductDto product : products) {
            product.setIdCategory(-1L);
            product.setPosition_progressive(0);
        }
        boolean flag = productUtils.saveAll(products, idAgency);
        if(!flag)
            throw new Exception("Errore modifica prodotti");
        // todo: aggiungere log su mongodb

        return 200;
    }

    @Transactional
    public int changeOrder(List<IdWithOrder> list) throws UnauthorizedException, Exception {
        long idUser = authUserProvider.getUserId();
        long idAgency = authUserProvider.getAgencyId();
        // Trova la categoria da spostare
        List<CategoryJpa> categories = categoryRepository.findByIdInAndIdAgencyAndDeleted(list.stream().map(IdWithOrder::getId).collect(Collectors.toList()), idAgency, false);
        if (categories.isEmpty()) {
            return 404; // Categorie non trovate // todo exception not found
        }

        Map<Long, Long> newOrderMap = list.stream()
                .collect(Collectors.toMap(IdWithOrder::getId, IdWithOrder::getOrder));

        // Aggiorna il positionProgressive di ogni categoria
        for (CategoryJpa category : categories) {
            if (newOrderMap.containsKey(category.getId())) {
                category.setPositionProgressive(newOrderMap.get(category.getId()).intValue());
            }
        }

        // Salva tutte le categorie in un'unica operazione
        categoryRepository.saveAll(categories);
        mongoCategoryRepository.save(new CategoryLog(LogOperation.OTHER, null, null, "function changeOrder " + list, idUser, idAgency));

        return 200; // Cambio ordine riuscito
    }


    @Transactional
    public int setAvailable(long idCategory, boolean available) throws UnauthorizedException, Exception {
        long idUser = authUserProvider.getUserId();
        long idAgency = authUserProvider.getAgencyId();
        Optional<CategoryJpa> categoryOptional = categoryRepository.findByIdAndIdAgencyAndDeleted(idCategory, idAgency, false);
        if (categoryOptional.isEmpty()) {
            ErrorLog.logger.error("category not found");
            return 404;
        }
        CategoryJpa category = categoryOptional.get();
        CategoryDto categoryDto = new CategoryDto(category, new ArrayList<>());
        category.setAvailable(available);
        categoryRepository.save(category);
        mongoCategoryRepository.save(new CategoryLog(LogOperation.OTHER, categoryDto, new CategoryDto(category, new ArrayList<>()), "function setAvailable value " + available + ". list product not given", idUser, idAgency));
        return 200;
    }

}
