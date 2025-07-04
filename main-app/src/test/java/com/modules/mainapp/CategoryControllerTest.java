package com.modules.mainapp;

import com.modules.categorymodule.requests.AddCategory;
import com.modules.categorymodule.requests.UpdateCategory;
import com.modules.common.dto.CategoryDto;
import com.modules.mainapp.controller.CategoryController;
import com.modules.categorymodule.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CategoryService categoryService;

    /******* INIZIO TEST addCategory ******/

    // Caso successo: ruolo ADMIN
    @Test
    @WithMockUser(roles = "ADMIN")  // Simula utente con ruolo ADMIN
    void addCategory_WithRoleAdmin_ShouldReturn200() throws Exception {
        when(categoryService.addCategory(any(AddCategory.class), any())).thenReturn(new CategoryDto());

        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", MediaType.TEXT_PLAIN_VALUE, "test data".getBytes());
        mockMvc.perform(multipart("/api/categories/addCategory")
                .file(file)
                .param("name", "Test Category")
                .param("description", "A test category description")
                .param("available", "true")
                .param("products", "1")
                .param("products", "2")
                .contentType(MediaType.MULTIPART_FORM_DATA)
        ).andExpect(status().isOk());
    }

    // Caso successo: ruolo ADMIN senza file
    @Test
    @WithMockUser(roles = "ADMIN")  // Simula utente con ruolo ADMIN
    void addCategory_WithRoleAdmin_ShouldReturn200_nofile() throws Exception {
        when(categoryService.addCategory(any(AddCategory.class), any())).thenReturn(new CategoryDto());

        MockMultipartFile file = null;
        mockMvc.perform(multipart("/api/categories/addCategory")
                .param("name", "Test Category")
                .param("description", "A test category description")
                .param("available", "true")
                .param("products", "1")
                .param("products", "2")
                .contentType(MediaType.MULTIPART_FORM_DATA)
        ).andExpect(status().isOk());
    }

    // Caso senza autenticazione - token mancante
    @Test
    void addCategory_WithoutAuth_ShouldReturn401() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", MediaType.TEXT_PLAIN_VALUE, "test data".getBytes());
        mockMvc.perform(multipart("/api/categories/addCategory")
                .file(file)
                .param("name", "Test Category")
                .param("description", "A test category description")
                .param("available", "true")
                .param("products", "1")
                .param("products", "2")
                .contentType(MediaType.MULTIPART_FORM_DATA)
        ).andExpect(status().isUnauthorized());
    }

    // Caso con ruolo diverso da ADMIN (ad es. USER)
    @Test
    @WithMockUser(roles = "USER")
    void addCategory_WithRoleUser_ShouldReturn403() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", MediaType.TEXT_PLAIN_VALUE, "test data".getBytes());
        mockMvc.perform(multipart("/api/categories/addCategory")
                .file(file)
                .param("name", "Test Category")
                .param("description", "A test category description")
                .param("available", "true")
                .param("products", "1")
                .param("products", "2")
                .contentType(MediaType.MULTIPART_FORM_DATA)
        ).andExpect(status().isForbidden());
    }

    /******* FINE TEST addCategory *******/


    /******* INIZIO TEST deleteCategory *******/

    @Test
    void deleteCategory_success() throws Exception {
        long categoryId = 1L;

        // Simula il comportamento del service
        Mockito.when(categoryService.deleteCategory(categoryId)).thenReturn(200);

        mockMvc.perform(get("/deleteCategory/{id}", categoryId))
                .andExpect(status().isOk())
                .andExpect(content().string("Category deleted successfully"));
    }

    @Test
    void deleteCategory_failure() throws Exception {
        long categoryId = 2L;

        // Simula fallimento (es. ritorna 400)
        Mockito.when(categoryService.deleteCategory(categoryId)).thenReturn(400);

        mockMvc.perform(get("/deleteCategory/{id}", categoryId))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Category deletion failed"));
    }

    @Test
    void deleteCategory_exception() throws Exception {
        long categoryId = 3L;

        // Simula un'eccezione nel service
        Mockito.when(categoryService.deleteCategory(categoryId))
                .thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/deleteCategory/{id}", categoryId))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Category deletion failed"));
    }

    /******* FINE TEST deleteCategory *******/



    /******* INIZIO TEST updateCategory *******/

    @Test
    void updateCategory_success_withoutFile() throws Exception {
        MockHttpServletRequestBuilder request = multipart("/updateCategory")
                .param("id", "1")
                .param("name", "Test Category")
                .param("description", "Some description")
                .param("available", "true")
                .param("products", "product1", "product2")
                .with(request1 -> { request1.setMethod("POST"); return request1; }); // multipart defaults to GET

        Mockito.when(categoryService.updateCategory(Mockito.any(UpdateCategory.class), Mockito.isNull()))
                .thenReturn(200);

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(content().string("Category updated successfully"));
    }

    // Caso con file
    @Test
    void updateCategory_success_withFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", "dummy-image-data".getBytes());

        MockHttpServletRequestBuilder request = multipart("/updateCategory")
                .file(file)
                .param("id", "2")
                .param("name", "Updated Category")
                .param("description", "Updated desc")
                .param("available", "false")
                .param("products", "prod1", "prod2")
                .with(req -> { req.setMethod("POST"); return req; });

        Mockito.when(categoryService.updateCategory(Mockito.any(UpdateCategory.class), Mockito.eq(file)))
                .thenReturn(200);

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(content().string("Category updated successfully"));
    }

    // Caso: update non valido (validate() == false)
    @Test
    void updateCategory_invalidModel() throws Exception {
        // Non passo i parametri necessari: simulate validate() == false
        MockHttpServletRequestBuilder request = multipart("/updateCategory")
                .with(req -> { req.setMethod("POST"); return req; });

        mockMvc.perform(request)
                .andExpect(status().isBadRequest());
    }

    // Caso: eccezione da service
    @Test
    void updateCategory_serviceThrowsException() throws Exception {
        MockHttpServletRequestBuilder request = multipart("/updateCategory")
                .param("id", "3")
                .param("name", "Category with issue")
                .param("description", "desc")
                .param("available", "true")
                .param("products", "prod1")
                .with(req -> { req.setMethod("POST"); return req; });

        Mockito.when(categoryService.updateCategory(Mockito.any(UpdateCategory.class), Mockito.isNull()))
                .thenThrow(new RuntimeException("DB failure"));

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Category update failed"));
    }

    /******* FINE TEST updateCategory *******/



    /******* INIZIO TEST changeOrder *******/
    void changeOrder_success() throws Exception {
        String json = """
            {
                "list": [
                    { "id": 1, "order": 2 },
                    { "id": 2, "order": 1 }
                ]
            }
        """;

        Mockito.when(categoryService.changeOrder(Mockito.anyList()))
                .thenReturn(200);

        mockMvc.perform(post("/changeOrder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().string("Order changed successfully"));
    }

    // Caso input non valido: validate() == false
    @Test
    void changeOrder_invalidInput_returnsBadRequest() throws Exception {
        String json = """
            {
                "list": []
            }
        """;

        mockMvc.perform(post("/changeOrder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    // Caso service lancia eccezione
    @Test
    void changeOrder_serviceThrowsException() throws Exception {
        String json = """
            {
                "list": [
                    { "id": 1, "order": 2 }
                ]
            }
        """;

        Mockito.when(categoryService.changeOrder(Mockito.anyList()))
                .thenThrow(new RuntimeException("Internal error"));

        mockMvc.perform(post("/changeOrder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Order update failed"));
    }
    /******* FINE TEST changeOrder *******/



    /******* INIZIO TEST setAvailable *******/
    @Test
    void setAvailable_validRequest_returnsOk() throws Exception {
        Mockito.when(categoryService.setAvailable(1L, true))
                .thenReturn(200);

        mockMvc.perform(get("/setAvailable/1/true"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    // idCategory <= 0 => ritorna 403 con messaggio
    @Test
    void setAvailable_invalidId_returns403() throws Exception {
        mockMvc.perform(get("/setAvailable/0/true"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Data not valid"));
    }

    // Service ritorna status != 200
    @Test
    void setAvailable_serviceFails_returnsErrore() throws Exception {
        Mockito.when(categoryService.setAvailable(1L, false))
                .thenReturn(500);

        mockMvc.perform(get("/setAvailable/1/false"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Errore"));
    }

    // Service lancia eccezione
    @Test
    void setAvailable_serviceThrowsException_returnsErrore() throws Exception {
        Mockito.when(categoryService.setAvailable(1L, true))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/setAvailable/1/true"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Errore"));
    }
    /******* FINE TEST setAvailable *******/
}
