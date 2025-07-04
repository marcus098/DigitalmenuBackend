package com.modules.categorymodule;

import com.modules.categorymodule.model.CategoryJpa;
import com.modules.categorymodule.model.CategoryLog;
import com.modules.categorymodule.repository.CategoryRepository;
import com.modules.categorymodule.repository.mongo.MongoCategoryRepository;
import com.modules.categorymodule.requests.AddCategory;
import com.modules.categorymodule.requests.UpdateCategory;
import com.modules.categorymodule.service.CategoryService;
import com.modules.common.dto.CategoryDto;
import com.modules.common.dto.ProductDto;
import com.modules.common.finders.FileUtils;
import com.modules.common.finders.ProductUtils;
import com.modules.common.model.IdWithOrder;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTests {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MongoCategoryRepository mongoCategoryRepository;

    @Mock
    private AuthenticatedUserProvider authUserProvider;

    @Mock
    private FileUtils fileUtils;

    @Mock
    private ProductUtils productUtils;

    @Mock
    private MultipartFile mockFile;

    @InjectMocks
    private CategoryService categoryService;

    private static final long USER_ID = 1L;
    private static final long AGENCY_ID = 100L;

    @BeforeEach
    void setUp() {
        // Configurazione di base per l'utente autenticato
        lenient().when(authUserProvider.getUserId()).thenReturn(USER_ID);
        lenient().when(authUserProvider.getAgencyId()).thenReturn(AGENCY_ID);
    }

    @Test
    void addCategory_Success_FirstCategory() throws Exception {
        // Given
        AddCategory addCategoryRequest = new AddCategory();
        addCategoryRequest.setName("Nuova Categoria");
        addCategoryRequest.setImage("image_url");

        // Simula che non ci siano altre categorie, quindi il progressivo sarà 1
        when(categoryRepository.findMaxPositionByAgency(AGENCY_ID)).thenReturn(null);
        // Simula il salvataggio della categoria e le assegna un ID
        when(categoryRepository.save(any(CategoryJpa.class))).thenAnswer(invocation -> {
            CategoryJpa category = invocation.getArgument(0);
            category.setId(1L); // Imposta un ID fittizio
            return category;
        });
        when(productUtils.findAllByIdCategoryAndDeletedAndIdAgency(anyLong(), anyLong())).thenReturn(new ArrayList<>());
        when(productUtils.saveAll(any(), anyLong())).thenReturn(true);

        // When
        CategoryDto result = categoryService.addCategory(addCategoryRequest, null);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Nuova Categoria", result.getName());
        assertEquals(1, result.getProgressiveNumber());

        verify(categoryRepository, times(1)).save(any(CategoryJpa.class));
        verify(mongoCategoryRepository, times(1)).save(any(CategoryLog.class));
    }

    @Test
    void addCategory_Success_WithFileAndProducts() throws Exception {
        // Given
        AddCategory addCategoryRequest = new AddCategory();
        addCategoryRequest.setName("Elettronica");

        ProductDto productToUpdate = new ProductDto();
        productToUpdate.setId(50L);
        productToUpdate.setIdCategory(0L); // Id categoria precedente

        List<ProductDto> products = new ArrayList<>();
        products.add(productToUpdate);

        when(categoryRepository.findMaxPositionByAgency(AGENCY_ID)).thenReturn(5);
        when(fileUtils.uploadImageWithBucket(mockFile, AGENCY_ID, USER_ID, "img")).thenReturn("new_image_url.jpg");
        when(categoryRepository.save(any(CategoryJpa.class))).thenAnswer(invocation -> {
            CategoryJpa cat = invocation.getArgument(0);
            cat.setId(2L);
            return cat;
        });
        when(productUtils.findAllByIdCategoryAndDeletedAndIdAgency(2L, AGENCY_ID)).thenReturn(products);
        when(productUtils.saveAll(any(), eq(AGENCY_ID))).thenReturn(true);


        // When
        CategoryDto result = categoryService.addCategory(addCategoryRequest, mockFile);

        // Then
        assertEquals(2L, result.getId());
        assertEquals("new_image_url.jpg", result.getImage());
        assertEquals(6, result.getProgressiveNumber()); // 5 (max) + 1
        assertEquals(1, result.getProducts().size());
        assertEquals(50L, result.getProducts().get(0));

        // Verifica che l'id della categoria sia stato impostato sul prodotto
        ArgumentCaptor<List<ProductDto>> productListCaptor = ArgumentCaptor.forClass(List.class);
        verify(productUtils).saveAll(productListCaptor.capture(), eq(AGENCY_ID));
        assertEquals(2L, productListCaptor.getValue().get(0).getIdCategory());

        verify(productUtils, times(1)).saveAllLogs(any());
    }

    @Test
    void updateCategory_NotFound() throws Exception {
        // Given
        UpdateCategory updateRequest = new UpdateCategory();
        updateRequest.setId(999L);
        when(categoryRepository.findByIdAndIdAgencyAndDeleted(999L, AGENCY_ID, false)).thenReturn(Optional.empty());

        // When
        int statusCode = categoryService.updateCategory(updateRequest, null);

        // Then
        assertEquals(404, statusCode);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateCategory_Success_WithProductChanges() throws Exception {
        // Given
        long categoryId = 1L;
        CategoryJpa existingCategory = new CategoryJpa();
        existingCategory.setId(categoryId);
        existingCategory.setName("Old Name");
        existingCategory.setIdAgency(AGENCY_ID);

        UpdateCategory updateRequest = new UpdateCategory();
        updateRequest.setId(categoryId);
        updateRequest.setName("New Name");
        updateRequest.setProducts(List.of("102|1", "103|2")); // P1 rimosso, P2 mantenuto, P3 aggiunto

        // Prodotti esistenti nella categoria
        ProductDto p1 = new ProductDto(); p1.setId(101L); p1.setIdCategory(categoryId);
        ProductDto p2 = new ProductDto(); p2.setId(102L); p2.setIdCategory(categoryId);
        List<ProductDto> oldProducts = List.of(p1, p2);

        // Nuovi prodotti basati sulla richiesta
        ProductDto p3 = new ProductDto(); p3.setId(103L); p3.setIdCategory(0L); // Prodotto nuovo
        List<ProductDto> newProductsFromRequest = List.of(p2, p3);

        when(categoryRepository.findByIdAndIdAgencyAndDeleted(categoryId, AGENCY_ID, false)).thenReturn(Optional.of(existingCategory));
        when(productUtils.findAllByIdCategoryAndDeletedAndIdAgency(categoryId, AGENCY_ID)).thenReturn(oldProducts);
        when(productUtils.findProductsByConditions(List.of(102L, 103L), AGENCY_ID)).thenReturn(newProductsFromRequest);

        // When
        int statusCode = categoryService.updateCategory(updateRequest, null);

        // Then
        assertEquals(200, statusCode);

        // Verifica che la categoria sia stata aggiornata
        ArgumentCaptor<CategoryJpa> categoryCaptor = ArgumentCaptor.forClass(CategoryJpa.class);
        verify(categoryRepository).save(categoryCaptor.capture());
        assertEquals("New Name", categoryCaptor.getValue().getName());

        // Verifica che i prodotti siano stati aggiornati correttamente
        ArgumentCaptor<List<ProductDto>> productsToSaveCaptor = ArgumentCaptor.forClass(List.class);
        verify(productUtils).saveAll(productsToSaveCaptor.capture(), eq(AGENCY_ID));

        List<ProductDto> savedProducts = productsToSaveCaptor.getValue();
        // Cerca i prodotti per verificare i loro stati finali
        ProductDto finalP1 = savedProducts.stream().filter(p -> p.getId() == 101L).findFirst().orElse(null);
        ProductDto finalP2 = savedProducts.stream().filter(p -> p.getId() == 102L).findFirst().orElse(null);
        ProductDto finalP3 = savedProducts.stream().filter(p -> p.getId() == 103L).findFirst().orElse(null);

        assertNotNull(finalP1);
        assertEquals(-1L, finalP1.getIdCategory()); // P1 rimosso

        assertNotNull(finalP2);
        assertEquals(categoryId, finalP2.getIdCategory()); // P2 mantenuto
        assertEquals(1, finalP2.getPosition_progressive());

        assertNotNull(finalP3);
        assertEquals(categoryId, finalP3.getIdCategory()); // P3 aggiunto
        assertEquals(2, finalP3.getPosition_progressive());
    }

    @Test
    void deleteCategory_Success() throws Exception {
        // Given
        long categoryIdToDelete = 5L;
        CategoryJpa category = new CategoryJpa();
        category.setId(categoryIdToDelete);
        category.setPositionProgressive(3);
        category.setIdAgency(AGENCY_ID);

        ProductDto product = new ProductDto();
        product.setId(201L);
        product.setIdCategory(categoryIdToDelete);
        List<ProductDto> productsInCategory = List.of(product);

        when(categoryRepository.findByIdAndIdAgencyAndDeleted(categoryIdToDelete, AGENCY_ID, false)).thenReturn(Optional.of(category));
        when(productUtils.findAllByIdCategoryAndDeletedAndIdAgency(categoryIdToDelete, AGENCY_ID)).thenReturn(productsInCategory);
        when(productUtils.saveAll(any(), eq(AGENCY_ID))).thenReturn(true);

        // When
        int statusCode = categoryService.deleteCategory(categoryIdToDelete);

        // Then
        assertEquals(200, statusCode);

        // Verifica che la categoria sia stata marcata come eliminata
        ArgumentCaptor<CategoryJpa> categoryCaptor = ArgumentCaptor.forClass(CategoryJpa.class);
        verify(categoryRepository).save(categoryCaptor.capture());
        assertTrue(categoryCaptor.getValue().isDeleted());
        assertNotNull(categoryCaptor.getValue().getDeletedAt());

        // Verifica che le posizioni successive siano state decrementate
        verify(categoryRepository).decrementPositions(AGENCY_ID, 4, Integer.MAX_VALUE);

        // Verifica che i prodotti siano stati "scollegati" dalla categoria
        ArgumentCaptor<List<ProductDto>> productListCaptor = ArgumentCaptor.forClass(List.class);
        verify(productUtils).saveAll(productListCaptor.capture(), eq(AGENCY_ID));
        assertEquals(-1L, productListCaptor.getValue().get(0).getIdCategory());
    }

    @Test
    void deleteCategory_NotFound() throws Exception {
        // Given
        long categoryIdToDelete = 999L;
        when(categoryRepository.findByIdAndIdAgencyAndDeleted(categoryIdToDelete, AGENCY_ID, false)).thenReturn(Optional.empty());

        // When
        int statusCode = categoryService.deleteCategory(categoryIdToDelete);

        // Then
        assertEquals(404, statusCode);
        verify(categoryRepository, never()).save(any());
        verify(productUtils, never()).saveAll(any(), anyLong());
    }

    @Test
    void changeOrder_Success() throws Exception {
        // Given
        IdWithOrder order1 = new IdWithOrder(); order1.setId(1L); order1.setOrder(2L);
        IdWithOrder order2 = new IdWithOrder(); order2.setId(2L); order2.setOrder(1L);
        List<IdWithOrder> newOrderList = List.of(order1, order2);

        CategoryJpa cat1 = new CategoryJpa(); cat1.setId(1L); cat1.setPositionProgressive(1);
        CategoryJpa cat2 = new CategoryJpa(); cat2.setId(2L); cat2.setPositionProgressive(2);
        List<CategoryJpa> categories = List.of(cat1, cat2);

        List<Long> ids = newOrderList.stream().map(IdWithOrder::getId).collect(Collectors.toList());
        when(categoryRepository.findByIdInAndIdAgencyAndDeleted(ids, AGENCY_ID, false)).thenReturn(categories);

        // When
        int statusCode = categoryService.changeOrder(newOrderList);

        // Then
        assertEquals(200, statusCode);

        ArgumentCaptor<List<CategoryJpa>> captor = ArgumentCaptor.forClass(List.class);
        verify(categoryRepository).saveAll(captor.capture());

        List<CategoryJpa> savedCategories = captor.getValue();
        assertEquals(2, savedCategories.stream().filter(c -> c.getId() == 1L).findFirst().get().getPositionProgressive());
        assertEquals(1, savedCategories.stream().filter(c -> c.getId() == 2L).findFirst().get().getPositionProgressive());

        verify(mongoCategoryRepository).save(any(CategoryLog.class));
    }

    @Test
    void setAvailable_Success() throws Exception {
        // Given
        long categoryId = 1L;
        boolean newAvailability = false;
        CategoryJpa category = new CategoryJpa();
        category.setId(categoryId);
        category.setAvailable(true); // Stato iniziale

        when(categoryRepository.findByIdAndIdAgencyAndDeleted(categoryId, AGENCY_ID, false)).thenReturn(Optional.of(category));

        // When
        int statusCode = categoryService.setAvailable(categoryId, newAvailability);

        // Then
        assertEquals(200, statusCode);

        ArgumentCaptor<CategoryJpa> categoryCaptor = ArgumentCaptor.forClass(CategoryJpa.class);
        verify(categoryRepository).save(categoryCaptor.capture());
        assertFalse(categoryCaptor.getValue().isAvailable());

        verify(mongoCategoryRepository).save(any(CategoryLog.class));
    }

    @Test
    void setAvailable_NotFound() throws Exception {
        // Given
        long categoryId = 999L;
        when(categoryRepository.findByIdAndIdAgencyAndDeleted(categoryId, AGENCY_ID, false)).thenReturn(Optional.empty());

        // When
        int statusCode = categoryService.setAvailable(categoryId, true);

        // Then
        assertEquals(404, statusCode);
        verify(categoryRepository, never()).save(any());
    }
}