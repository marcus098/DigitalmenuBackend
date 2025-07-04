package com.modules.mainapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modules.common.dto.IngredientDto;
import com.modules.ingredientmodule.requests.AddIngredient;
import com.modules.ingredientmodule.requests.UpdateIngredient;
import com.modules.ingredientmodule.service.IngredientService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class IngredientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IngredientService ingredientService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /****** INIZIO TEST addIngredient ******/
    @Test
    void addIngredient_success_returns200WithFullDto() throws Exception {
        AddIngredient input = new AddIngredient();
        input.setName("Mozzarella");
        input.setAvailable(true);
        input.setAddable(true);
        input.setFrozen(false);
        input.setPrice(1.5);
        input.setAllergens(List.of(1, 7));

        IngredientDto output = new IngredientDto();
        output.setId(10L);
        output.setName("Mozzarella");
        output.setAvailable(true);
        output.setAddable(true);
        output.setFrozen(false);
        output.setPrice(1.5);
        output.setAllergens(List.of(1, 7));

        Mockito.when(ingredientService.addIngredient(Mockito.any())).thenReturn(output);

        mockMvc.perform(post("/insert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.name").value("Mozzarella"))
                .andExpect(jsonPath("$.data.price").value(1.5))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.addable").value(true))
                .andExpect(jsonPath("$.data.frozen").value(false))
                .andExpect(jsonPath("$.data.allergens[0]").value(1))
                .andExpect(jsonPath("$.data.allergens[1]").value(7));
    }

    // Caso fallimento (ingredientDto == null)
    @Test
    void addIngredient_nullResponse_returns400() throws Exception {
        AddIngredient input = new AddIngredient();
        input.setName("Mozzarella");
        input.setAvailable(true);
        input.setAddable(true);
        input.setFrozen(false);
        input.setPrice(1.5);
        input.setAllergens(List.of(1, 7));

        Mockito.when(ingredientService.addIngredient(Mockito.any())).thenReturn(null);

        mockMvc.perform(post("/insert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data").doesNotExist());
    }
    /****** FINE TEST addIngredient ******/


    /****** INIZIO TEST updateIngredient ******/
    @Test
    @WithMockUser(roles = "ADMIN")
    void updateIngredient_success_returns200WithDto() throws Exception {
        UpdateIngredient input = new UpdateIngredient();
        input.setId(5L);
        input.setName("Prosciutto");
        input.setAvailable(true);
        input.setAddable(false);
        input.setFrozen(false);
        input.setPrice(2.0);
        input.setAllergens(List.of(2, 4));

        IngredientDto output = new IngredientDto();
        output.setId(5L);
        output.setName("Prosciutto");
        output.setAvailable(true);
        output.setAddable(false);
        output.setFrozen(false);
        output.setPrice(2.0);
        output.setAllergens(List.of(2, 4));

        Mockito.when(ingredientService.updateIngredient(Mockito.any())).thenReturn(output);

        mockMvc.perform(post("/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.name").value("Prosciutto"))
                .andExpect(jsonPath("$.data.price").value(2.0))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.addable").value(false))
                .andExpect(jsonPath("$.data.frozen").value(false))
                .andExpect(jsonPath("$.data.allergens[0]").value(2))
                .andExpect(jsonPath("$.data.allergens[1]").value(4));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateIngredient_nullResponse_returns400() throws Exception {
        UpdateIngredient input = new UpdateIngredient();
        input.setId(5L);
        input.setName("Prosciutto");

        Mockito.when(ingredientService.updateIngredient(Mockito.any())).thenReturn(null);

        mockMvc.perform(post("/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void updateIngredient_withNonAdminRole_returnsForbidden() throws Exception {
        UpdateIngredient input = new UpdateIngredient();
        input.setId(5L);
        input.setName("Prosciutto");

        mockMvc.perform(post("/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isForbidden());
    }


    /****** FINE TEST updateIngredient ******/


    /****** INIZIO TEST deleteIngredient ******/
    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteIngredient_success_returns200WithSuccessMessage() throws Exception {
        long id = 1L;

        Mockito.when(ingredientService.deleteIngredient(id)).thenReturn(200);

        mockMvc.perform(get("/delete/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string("Ingredient deleted"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteIngredient_failure_returns400WithErrorMessage() throws Exception {
        long id = 1L;

        Mockito.when(ingredientService.deleteIngredient(id)).thenReturn(400);

        mockMvc.perform(get("/delete/{id}", id))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Error"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteIngredient_invalidId_throwsException() throws Exception {
        long invalidId = -1L;

        mockMvc.perform(get("/delete/{id}", invalidId))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void deleteIngredient_withNonAdminRole_returnsForbidden() throws Exception {
        long id = 1L;

        mockMvc.perform(get("/delete/{id}", id))
                .andExpect(status().isForbidden());
    }

    /****** FINE TEST deleteIngredient ******/


    /****** INIZIO TEST setAddable ******/
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/setAddable/{idIngredient}/{addable}")
    public ResponseEntity<String> setAddable(@PathVariable("idIngredient") long idIngredient,
                                             @PathVariable("addable") boolean addable) {
        if (idIngredient <= 0)
            throw new IllegalArgumentException("Errore valori ricevuti");
        int status = ingredientService.setAddable(idIngredient, addable);
        return ResponseEntity.status(status).body(status == 200 ? "OK" : "Errore");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setAddable_failure_returns400WithErrore() throws Exception {
        long idIngredient = 1L;
        boolean addable = false;

        Mockito.when(ingredientService.setAddable(idIngredient, addable)).thenReturn(400);

        mockMvc.perform(get("/setAddable/{idIngredient}/{addable}", idIngredient, addable))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Errore"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setAddable_invalidId_throwsException() throws Exception {
        long invalidId = 0L;
        boolean addable = true;

        mockMvc.perform(get("/setAddable/{idIngredient}/{addable}", invalidId, addable))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void setAddable_nonAdminRole_returnsForbidden() throws Exception {
        long idIngredient = 1L;
        boolean addable = true;

        mockMvc.perform(get("/setAddable/{idIngredient}/{addable}", idIngredient, addable))
                .andExpect(status().isForbidden());
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WAITER')")
    @GetMapping("/setAvailable/{idIngredient}/{available}")
    public ResponseEntity<String> setAvailable(@PathVariable("idIngredient") long idIngredient,
                                               @PathVariable("available") boolean available) {
        if (idIngredient <= 0) {
            throw new IllegalArgumentException("Errore valori ricevuti");
        }
        int status = ingredientService.setAvailable(idIngredient, available);
        return ResponseEntity.status(status).body(status == 200 ? "OK" : "Errore");
    }
    /****** FINE TEST setAddable ******/


    /****** INIZIO TEST setAvailable ******/

    @Test
    @WithMockUser(roles = "WAITER")
    void setAvailable_successWaiter_returns200Ok() throws Exception {
        long idIngredient = 1L;
        boolean available = false;

        Mockito.when(ingredientService.setAvailable(idIngredient, available)).thenReturn(200);

        mockMvc.perform(get("/setAvailable/{idIngredient}/{available}", idIngredient, available))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }
    @Test
    @WithMockUser(roles = "ADMIN")
    void setAvailable_failure_returns400Errore() throws Exception {
        long idIngredient = 1L;
        boolean available = false;

        Mockito.when(ingredientService.setAvailable(idIngredient, available)).thenReturn(400);

        mockMvc.perform(get("/setAvailable/{idIngredient}/{available}", idIngredient, available))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Errore"));
    }
    @Test
    @WithMockUser(roles = "ADMIN")
    void setAvailable_invalidId_throwsException() throws Exception {
        long invalidId = 0L;
        boolean available = true;

        mockMvc.perform(get("/setAvailable/{idIngredient}/{available}", invalidId, available))
                .andExpect(status().isInternalServerError());
    }
    @Test
    @WithMockUser(roles = "USER")
    void setAvailable_unauthorizedRole_returnsForbidden() throws Exception {
        long idIngredient = 1L;
        boolean available = true;

        mockMvc.perform(get("/setAvailable/{idIngredient}/{available}", idIngredient, available))
                .andExpect(status().isForbidden());
    }

    /****** FINE TEST setAvailable ******/


}
