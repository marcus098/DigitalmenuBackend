//package com.modules.webfluxmodule.services;
//
//import com.modules.common.dto.*;
//import com.modules.common.mapper.CategoryMapper;
//import com.modules.common.mapper.IngredientMapper;
//import com.modules.common.mapper.ProductMapper;
//import com.modules.common.model.enums.TypeEntity;
//import com.modules.common.utilities.Utilities;
//import com.modules.webfluxmodule.models.db.CategoryR2dbc;
//import com.modules.webfluxmodule.repositories.*;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.Sinks;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//@Service
//public class NotificationService {
//
//    @Autowired
//    private WebfluxCategoryRepository webfluxCategoryRepository;
//    @Autowired
//    private WebfluxProductRepository webfluxProductRepository;
//    @Autowired
//    private WebfluxIngredientRepository webfluxIngredientRepository;
//    @Autowired
//    private ProductMapper productMapper;
//    @Autowired
//    private CategoryMapper categoryMapper;
//    @Autowired
//    private IngredientMapper ingredientMapper;
//
//    @Autowired
//    private NotificationSinkManager sinkManager;
//    private final String CATEGORIES = "categoriesList";
//    private final String PRODUCTS = "productsList";
//    private final String INGREDIENTS = "ingredientsList";
//    private final String STYLES = "style";
//    private final String TABLES = "tablesList";
//    private final String ORDERS = "ordersList";
//    private final String IMAGES = "imagesList";
//    @Autowired
//    private WebfluxTableRepository webfluxTableRepository;
//    @Autowired
//    private WebfluxAgencyRepository webfluxAgencyRepository;
//    @Autowired
//    private WebfluxImageRepository webfluxImageRepository;
//    @Autowired
//    private Utilities utilities;
//
//
//    public void notifyUpdate(long idAgency, List<TypeEntity> typeEntities) {
//        // Mappa per raccogliere i flussi di dati per ogni entità
//        Map<String, Flux<?>> updatesForAuthenticated = new HashMap<>();
//        Map<String, Flux<?>> updatesForUnauthenticated = new HashMap<>();
//
//        for (TypeEntity typeEntity : typeEntities) {
//            if (typeEntity == TypeEntity.CATEGORY) {
//                Flux<CategoryDto> categoryDtoFlux = getCategoryFlux(idAgency, true);
//                updatesForAuthenticated.put(CATEGORIES, categoryDtoFlux);
//                updatesForUnauthenticated.put(CATEGORIES, categoryDtoFlux.map(CategoryDto::isAvailable));
//            }
//            if (typeEntity == TypeEntity.PRODUCT) {
//                Flux<ProductDto> productDtoFlux = getProductFlux(idAgency, true);
//                updatesForAuthenticated.put(PRODUCTS, productDtoFlux);
//                updatesForUnauthenticated.put(PRODUCTS, productDtoFlux.map(ProductDto::isAvailable));
//            }
//            if (typeEntity == TypeEntity.INGREDIENT) {
//                Flux<IngredientDto> ingredientDtoFlux = getIngredientFlux(idAgency, true);
//                updatesForAuthenticated.put(INGREDIENTS, ingredientDtoFlux);
//                updatesForUnauthenticated.put(INGREDIENTS, ingredientDtoFlux.map(IngredientDto::isAvailable));
//            }
//            if (typeEntity == TypeEntity.STYLE) {
//                Flux<StyleDto> styleDtoFlux = getStyleFlux(idAgency);
//                updatesForAuthenticated.put(STYLES, styleDtoFlux);
//                updatesForUnauthenticated.put(STYLES, styleDtoFlux);
//            }
//            if (typeEntity == TypeEntity.TABLE) {
//                Flux<TableDto> tableDtoFlux = getTableFlux(idAgency);
//                updatesForAuthenticated.put(TABLES, tableDtoFlux); // Solo autenticati
//            }
//            if (typeEntity == TypeEntity.IMAGE) {
//                Flux<ImageDto> imageDtoFlux = getImageFlux(idAgency);
//                updatesForAuthenticated.put(IMAGES, imageDtoFlux); // Solo autenticati
//            }
////            if(typeEntity == TypeEntity.ORDER) {
////                Flux<>
////                updatesForAuthenticated.put("orders", getOrderFlux(idAgency)); // Solo autenticati
//            //           }
//        }
//
//        // Invia notifiche agli utenti autenticati
//        sendNotifications(idAgency, updatesForAuthenticated, true);
//
//        // Invia notifiche agli utenti non autenticati
//        sendNotifications(idAgency, updatesForUnauthenticated, false);
//    }
//
//    private void sendNotifications(long idAgency, Map<String, Flux<?>> updatesMap, boolean isAuthenticated) {
//        Flux<Map<String, Object>> notificationFlux = Flux.fromIterable(updatesMap.entrySet())
//                .flatMap(entry -> entry.getValue()
//                        .collectList()
//                        .map(dataList -> Map.of(entry.getKey(), dataList))
//                )
//                .collectList()
//                .map(notifications -> {
//                    Map<String, Object> aggregatedNotifications = new HashMap<>();
//                    notifications.forEach(aggregatedNotifications::putAll);
//                    return aggregatedNotifications;
//                })
//                .flux();
//
//        // Identifica il sink corretto
//        //notificationFlux.subscribe(notificationData -> {
//        //    Sinks.Many<Map<String, Object>> sink = isAuthenticated
//        //            ? sinkManager.getAuthenticatedSink(idAgency)
//        //            : sinkManager.getUnauthenticatedSink(idAgency);
//        //    if (sink != null) {
//        //        sink.tryEmitNext(notificationData);
//        //    }
//        //});
//    }
//
//
//    private Flux<CategoryDto> getCategoryFlux(long idAgency, boolean isAuthenticated) {
//        if (isAuthenticated) {
//            return webfluxCategoryRepository.findCategoriesWithProductsByIdAgencyAndDeleted(idAgency, false)
//                    .map(c -> categoryMapper.toDto(
//                            new CategoryR2dbc(c.getCategory_id(), c.getCategory_name(), c.getCategory_description(), -1L, c.getCategory_progressive_number(), c.isCategory_available(), c.getCategory_image()), utilities.convertStringToLongIntegerList(c.getProducts())));
//        } else {
//            return webfluxCategoryRepository.findByIdAgencyAndDeletedAndAvailable(idAgency, false, true)
//                    .map(category -> new CategoryDto(category, new ArrayList<>()));
//        }
//    }
//
//    private Flux<ProductDto> getProductFlux(long idAgency, boolean isAuthenticated) {
//        if (isAuthenticated) {
//            return webfluxProductRepository.findAllByIdAgencyAndDeleted(idAgency, false)
//                    .map(productR2dbc -> productMapper.toDto(productR2dbc));
//        } else {
//            return webfluxProductRepository.findAllByIdAgencyAndDeletedAndAvailable(idAgency, false, true)
//                    .map(productR2dbc -> productMapper.toDto(productR2dbc));
//        }
//    }
//
//    private Flux<IngredientDto> getIngredientFlux(long idAgency, boolean isAuthenticated) {
//        if (isAuthenticated) {
//            return webfluxIngredientRepository.findByIdAgencyAndDeleted(idAgency, false)
//                    .map(ingredientR2dbc -> ingredientMapper.toDto(ingredientR2dbc));
//        } else {
//            return webfluxIngredientRepository.findByIdAgencyAndDeletedAndAvailable(idAgency, false, true)
//                    .map(ingredientR2dbc -> ingredientMapper.toDto(ingredientR2dbc));
//        }
//    }
//
//    private Flux<TableDto> getTableFlux(long idAgency) {
//        return webfluxTableRepository.findByIdAgencyAndDeleted(idAgency, false)
//                .map(TableDto::new);
//    }
//
//    private Flux<ImageDto> getImageFlux(long idAgency) {
//        return webfluxImageRepository.findByIdAgencyAndDeleted(idAgency, false)
//                .map(ImageDto::new);
//    }
//
//    private Flux<StyleDto> getStyleFlux(long idAgency) {
//        return webfluxAgencyRepository.findAllByIdAndDeleted(idAgency, false)
//                .map(StyleDto::new);
//    }
//
//}
