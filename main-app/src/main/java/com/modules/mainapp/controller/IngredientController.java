package com.modules.mainapp.controller;

import com.modules.common.dto.IngredientDto;
import com.modules.common.responses.DataResponse;
import com.modules.ingredientmodule.requests.AddIngredient;
import com.modules.ingredientmodule.requests.UpdateIngredient;
import com.modules.ingredientmodule.service.IngredientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@CrossOrigin(origins = "*")
@RequestMapping("/api/ingredients")
@RestController
public class IngredientController {
    @Autowired
    private IngredientService ingredientService;

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/insert")
    public ResponseEntity<DataResponse<IngredientDto>> addIngredient(@RequestBody AddIngredient addIngredient) {
        IngredientDto ingredientDto = ingredientService.addIngredient(addIngredient);
        return ResponseEntity.status(ingredientDto == null ? 400 : 200).body(new DataResponse<IngredientDto>(ingredientDto));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/update")
    public ResponseEntity<DataResponse<IngredientDto>> updateIngredient(@RequestBody UpdateIngredient updateIngredient) {
        IngredientDto ingredientDto = ingredientService.updateIngredient(updateIngredient);
        return ResponseEntity.status(ingredientDto == null ? 400 : 200).body(new DataResponse<IngredientDto>(ingredientDto));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/delete/{id}")
    public ResponseEntity<DataResponse<List<Long>>> deleteIngredient(@PathVariable("id") long id) { // ritorno la lista di id dei prodotti da modificare lato frontend
        if (id <= 0)
            throw new IllegalArgumentException("Errore valori ricevuti");
        List<Long> products = ingredientService.deleteIngredient(id);
        return ResponseEntity.status(products != null ? 200 : 400).body(new DataResponse<>(products != null ? products : new ArrayList<Long>()));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/setAddable/{idIngredient}/{addable}")
    public ResponseEntity<String> setAddable(@PathVariable("idIngredient") long idIngredient,
                                             @PathVariable("addable") boolean addable) {
        if (idIngredient <= 0)
            throw new IllegalArgumentException("Errore valori ricevuti");
        int status = ingredientService.setAddable(idIngredient, addable);
        return ResponseEntity.status(status).body(status == 200 ? "OK" : "Errore");
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

}