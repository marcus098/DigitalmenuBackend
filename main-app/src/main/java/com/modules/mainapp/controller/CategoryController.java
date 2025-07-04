package com.modules.mainapp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modules.categorymodule.kafka.CategoryUpdateProducer;
import com.modules.categorymodule.requests.AddCategory;
import com.modules.categorymodule.requests.ChangeOrder;
import com.modules.categorymodule.requests.UpdateCategory;
import com.modules.categorymodule.service.CategoryService;
import com.modules.common.dto.CategoryDto;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.responses.DataResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RestController
@CrossOrigin
@RequestMapping("/api/categories")
public class CategoryController {
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private CategoryUpdateProducer categoryUpdateProducer;
    @Autowired
    private ObjectMapper objectMapper;

    @GetMapping("/api/public/test")
    public String test() {
        try {
            CategoryDto categoryDto = new CategoryDto();
            categoryDto.setName("test");
            categoryDto.setId(1);
            categoryDto.setSessionUpdating("4_d4b68f7d-ebd2-4139-b819-bcdbe9a27911");
            categoryDto.setChangeType("insert");
            categoryDto.setDescription("Test");
            sendCategoryUpdates(new ArrayList<>(Arrays.asList(categoryDto)));
        } catch (Exception e) {

        }
        return "test";
    }

    public void sendCategoryUpdates(List<CategoryDto> categoryDtoList) {
        for (CategoryDto categoryDto : categoryDtoList) {
            try {
                categoryUpdateProducer.sendUpdate(objectMapper.writeValueAsString(categoryDto)); // Riutilizza il metodo per l'invio singolo
            } catch (Exception e) {

            }
        }
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @PostMapping("/addCategory")
    public ResponseEntity<?> addCategory(
            @ModelAttribute AddCategory addCategory,
            @RequestParam(required = false) MultipartFile file
    ) {
        if (!addCategory.validate())
            return ResponseEntity.badRequest().body(null);
        CategoryDto categoryDto = null;
        try {
            categoryDto = categoryService.addCategory(addCategory, file);
        } catch (Exception e) {
            ErrorLog.logger.error("Errore addCategory", e);
        }
        return ResponseEntity.status(categoryDto == null ? 400 : 200).body(new DataResponse<>(categoryDto));
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @GetMapping("/deleteCategory/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable("id") long id) {
        int status = 400;
        try {
            status = categoryService.deleteCategory(id);
        } catch (Exception e) {
            ErrorLog.logger.error("Errore deleteCategory", e);
        }
        return ResponseEntity.status(status).body(status == 200 ? "Category deleted successfully" : "Category deletion failed");
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @PostMapping("/updateCategory")
    public ResponseEntity<?> updateCategory(
            @ModelAttribute UpdateCategory updateCategory,
            @RequestParam(required = false) MultipartFile file
    ) {
        if (!updateCategory.validate())
            return ResponseEntity.badRequest().body(null);
        CategoryDto categoryDto = null;
        try {
            categoryDto = categoryService.updateCategory(updateCategory, file);
        } catch (Exception e) {
            ErrorLog.logger.error("Errore updateCategory", e);
        }
        return ResponseEntity.status(categoryDto == null ? 400 : 200).body(categoryDto != null ? new DataResponse<>(categoryDto) : "Category update failed");
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @PostMapping("/changeOrder")
    public ResponseEntity<?> changeOrder(@RequestBody ChangeOrder changeOrder) {
        if (!changeOrder.validate())
            return ResponseEntity.badRequest().body(null);
        int status = 400;
        try {
            status = categoryService.changeOrder(changeOrder.getList());
        } catch (Exception e) {
            ErrorLog.logger.error("Errore changeOrder", e);
        }
        return ResponseEntity.status(status).body(status == 200 ? "Order changed successfully" : "Order update failed");
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WAITER')")
    @GetMapping("/setAvailable/{idCategory}/{available}")
    public ResponseEntity<String> setAvailable(@PathVariable("idCategory") long idCategory,
                                               @PathVariable("available") boolean available) {
        if (idCategory <= 0)
            return ResponseEntity.status(403).body("Data not valid");
        int status = 400;
        try {
            status = categoryService.setAvailable(idCategory, available);
        } catch (Exception e) {
            ErrorLog.logger.error("Errore setAvailable", e);
        }
        return ResponseEntity.status(status).body(status == 200 ? "OK" : "Errore");
    }
}

