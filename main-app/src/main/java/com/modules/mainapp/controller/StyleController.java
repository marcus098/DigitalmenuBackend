package com.modules.mainapp.controller;

import com.modules.common.responses.DataResponse;
import com.modules.stylemodule.requests.UpdateStyle;
import com.modules.stylemodule.service.StyleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
@CrossOrigin
@RequestMapping("/api/style")
public class StyleController {
    @Autowired
    private StyleService styleService;

    @PostMapping("/update")
    public ResponseEntity<?> updateStyle(@ModelAttribute UpdateStyle updateStyle,
                                         @RequestParam(name = "logoFile", required = false) MultipartFile logoFile,
                                         @RequestParam(name = "heroFile", required = false) MultipartFile heroFile){
        return ResponseEntity.ok(new DataResponse<>(styleService.updateStyle(updateStyle, logoFile, heroFile)));
    }
}