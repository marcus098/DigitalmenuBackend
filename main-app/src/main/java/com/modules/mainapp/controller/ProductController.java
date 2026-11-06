package com.modules.mainapp.controller;

import com.modules.common.dto.ProductDto;
import com.modules.common.dto.UserDto;
import com.modules.common.model.OptionInProduct;
import com.modules.common.model.variant.VariantGroup;
import com.modules.common.model.variant.VariantGroups;
import com.modules.common.responses.DataResponse;
import com.modules.common.utilities.Utilities;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.productmodule.requests.AddProduct;
import com.modules.productmodule.requests.ChangeOrder;
import com.modules.productmodule.requests.UpdateProduct;
import com.modules.productmodule.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RequestMapping("/api/products")
@RestController
public class ProductController {
    @Autowired
    private ProductService productService;
    @Autowired
    private Utilities utilities;

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/addProduct")
    public ResponseEntity<?> addProduct(
            @ModelAttribute AddProduct addProduct,
            @RequestParam(value = "options", required = false) List<String> optionsJsonList,
            @RequestParam(value = "variantGroups", required = false) String variantGroupsJson,
            @RequestParam(required = false) MultipartFile file
    ) {
        List<OptionInProduct> optionList = utilities.convertOptionsRequestToPtions(optionsJsonList);

        if(optionList == null || !addProduct.validate())
            return ResponseEntity.badRequest().body(null);
        List<VariantGroup> variantGroups;
        try {
            variantGroups = VariantGroups.parseRequest(variantGroupsJson);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
        ProductDto productDto = productService.addProduct(addProduct, file, optionList, variantGroups);
        return ResponseEntity.status(productDto == null ? 400 : 200).body(new DataResponse<ProductDto>(null, productDto));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/updateProduct")
    public ResponseEntity<?> updateProduct(
            @ModelAttribute UpdateProduct updateProduct,
            @RequestParam(value = "options", required = false) List<String> optionsJsonList,
            @RequestParam(value = "variantGroups", required = false) String variantGroupsJson,
            @RequestParam(required = false) MultipartFile file
    ) {
        List<OptionInProduct> optionList = utilities.convertOptionsRequestToPtions(optionsJsonList);
        if(optionList == null || !updateProduct.validate())
            return ResponseEntity.badRequest().body(null);
        // Campo assente = gruppi invariati; "[]" = rimuovi tutti
        List<VariantGroup> variantGroups = null;
        if (variantGroupsJson != null) {
            try {
                variantGroups = VariantGroups.parseRequest(variantGroupsJson);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
            }
        }
        ProductDto productDto = productService.updateProduct(updateProduct, file, optionList, variantGroups);
        return ResponseEntity.status(productDto == null ? 400 : 200).body(new DataResponse<ProductDto>(null, productDto));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/deleteProduct/{idProduct}")
    public ResponseEntity<String> deleteProduct(@PathVariable("idProduct") long idProduct) {
        if (idProduct <= 0)
            throw new IllegalArgumentException("Errore valori ricevuti");
        int status = productService.deleteProduct(idProduct);

        return ResponseEntity.status(status).body(status == 200 ? "OK" : "Errore");
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/changeOrder")
    public ResponseEntity<?> changeOrder(@RequestBody ChangeOrder changeOrder) {
        if (!changeOrder.validate())
            return ResponseEntity.badRequest().body(null);
        int status = 400;
        try {
            status = productService.changeOrder(changeOrder.getList());
        } catch (Exception e) {
            ErrorLog.logger.error("Errore changeOrder product", e);
        }
        return ResponseEntity.status(status).body(status == 200 ? "Order changed successfully" : "Order update failed");
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WAITER')")
    @GetMapping("/setAvailable/{idProduct}/{available}")
    public ResponseEntity<String> setAvailable(
            @PathVariable("idProduct") long idProduct,
            @PathVariable("available") boolean available
    ) {
        if (idProduct <= 0)
            throw new IllegalArgumentException("Errore valori ricevuti");
        int status = productService.setAvailable(idProduct, available);
        return ResponseEntity.status(status).body(status == 200 ? "OK" : "Errore");
    }

    /** Esaurisce/ripristina una scelta di un gruppo di varianti (es. impasto senza glutine finito). */
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WAITER')")
    @GetMapping("/setVariantAvailable/{idProduct}/{groupId}/{choiceId}/{available}")
    public ResponseEntity<String> setVariantAvailable(
            @PathVariable("idProduct") long idProduct,
            @PathVariable("groupId") String groupId,
            @PathVariable("choiceId") String choiceId,
            @PathVariable("available") boolean available
    ) {
        if (idProduct <= 0 || groupId.isBlank() || choiceId.isBlank())
            throw new IllegalArgumentException("Errore valori ricevuti");
        return switch (productService.setVariantAvailable(idProduct, groupId, choiceId, available)) {
            case OK -> ResponseEntity.ok("OK");
            case PRODUCT_NOT_FOUND -> ResponseEntity.status(404).body("Prodotto non trovato");
            case CHOICE_NOT_FOUND -> ResponseEntity.status(404).body("Variante non trovata");
            case ERROR -> ResponseEntity.status(400).body("Errore");
        };
    }

}
