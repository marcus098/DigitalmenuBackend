package com.modules.mainapp.controller;

import com.modules.common.responses.DataResponse;
import com.modules.stylemodule.requests.UpdateStyle;
import com.modules.stylemodule.service.StyleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Controller
@CrossOrigin
@RequestMapping("/api/style")
// Solo il titolare modifica stile e sito vetrina (i camerieri leggono lo stile dal server pubblico)
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class StyleController {
    @Autowired
    private StyleService styleService;

    @PostMapping("/update")
    public ResponseEntity<?> updateStyle(@ModelAttribute UpdateStyle updateStyle,
                                         @RequestParam(name = "logoFile", required = false) MultipartFile logoFile,
                                         @RequestParam(name = "heroFile", required = false) MultipartFile heroFile){
        return ResponseEntity.ok(new DataResponse<>(styleService.updateStyle(updateStyle, logoFile, heroFile)));
    }

    /** Foto del sito vetrina: risponde con il percorso nel bucket da salvare nel landingConfig. */
    @PostMapping("/upload-image")
    public ResponseEntity<?> uploadImage(@RequestParam(name = "file", required = false) MultipartFile file) {
        return ResponseEntity.ok(new DataResponse<>(Map.of("path", styleService.uploadLandingImage(file))));
    }
}