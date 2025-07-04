package com.modules.mainapp.controller;

import com.modules.common.dto.ProductDto;
import com.modules.common.dto.UserDto;
import com.modules.common.model.OptionInProduct;
import com.modules.common.responses.DataResponse;
import com.modules.common.utilities.Utilities;
import com.modules.productmodule.requests.AddProduct;
import com.modules.productmodule.requests.UpdateProduct;
import com.modules.productmodule.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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
    public ResponseEntity<DataResponse<ProductDto>> addProduct(
            @ModelAttribute AddProduct addProduct,
            @RequestParam(value = "options", required = false) List<String> optionsJsonList,
            @RequestParam(required = false) MultipartFile file
    ) {
        List<OptionInProduct> optionList = utilities.convertOptionsRequestToPtions(optionsJsonList);

        if(optionList == null || !addProduct.validate())
            return ResponseEntity.badRequest().body(null);
        ProductDto productDto = productService.addProduct(addProduct, file, optionList);
        return ResponseEntity.status(productDto == null ? 400 : 200).body(new DataResponse<ProductDto>(null, productDto));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/updateProduct")
    public ResponseEntity<DataResponse<ProductDto>> updateProduct(
            @ModelAttribute UpdateProduct updateProduct,
            @RequestParam(value = "options", required = false) List<String> optionsJsonList,
            @RequestParam(required = false) MultipartFile file
    ) {
        List<OptionInProduct> optionList = utilities.convertOptionsRequestToPtions(optionsJsonList);
        if(optionList == null || !updateProduct.validate())
            return ResponseEntity.badRequest().body(null);
        ProductDto productDto = productService.updateProduct(updateProduct, file, optionList);
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

}
