package com.modules.mainapp.controller;

import com.modules.categorymodule.requests.AddCategory;
import com.modules.common.responses.DataResponse;
import com.modules.filemodule.requests.AddFile;
import com.modules.filemodule.service.FileService;
import com.modules.filemodule.service.FolderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
@CrossOrigin
@RequestMapping("/api/filemanager")
public class FileController {
    @Autowired
    private FolderService folderService;
    @Autowired
    private FileService fileService;

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/files/{folderId}")
    public ResponseEntity<?> getAllFiles(@PathVariable("folderId") long id) {
        return ResponseEntity.ok(new DataResponse<>(fileService.getFilesByFolderId(id)));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/files/download/{id}")
    public ResponseEntity<?> downloadFile(@PathVariable("id") long id) {
        return ResponseEntity.ok(new DataResponse<>(fileService.downloadFile(id)));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/files/add")
    public ResponseEntity<?> addFile(@ModelAttribute AddFile addFile,
                                     @RequestParam(required = false) MultipartFile file) {
        if (file == null)
            return ResponseEntity.badRequest().body("file vuoto");
        return ResponseEntity.ok(new DataResponse<>(fileService.addFile(addFile, file)));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/files/delete/{id}")
    public ResponseEntity<?> deleteFile(@PathVariable("id") long id) {
        return ResponseEntity.ok(new DataResponse<>(fileService.deleteFile(id)));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/files/rename/{id}/{name}")
    public ResponseEntity<?> renameFile(@PathVariable("id") long id, @PathVariable("name") String name) {
        return ResponseEntity.ok(new DataResponse<>(fileService.renameFile(id, name)));
    }


    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/folders/delete/{id}")
    public ResponseEntity<?> deleteFolder(@PathVariable("id") long id) {
        return ResponseEntity.ok(new DataResponse<>(folderService.deleteFolder(id, false)));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @DeleteMapping("/folders/forcedelete/{id}")
    public ResponseEntity<?> deleteForceFolder(@PathVariable("id") long id) {
        return ResponseEntity.ok(new DataResponse<>(folderService.deleteFolder(id, true)));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/folders/add/{name}")
    public ResponseEntity<?> addFolder(@PathVariable("name") String name) {
        return ResponseEntity.ok(new DataResponse<>(folderService.addFolder(name)));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/folders/rename/{id}/{name}")
    public ResponseEntity<?> renameFolder(@PathVariable("id") long id, @PathVariable("name") String name) {
        return ResponseEntity.ok(new DataResponse<>(folderService.renameFolder(id, name)));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/folders/getall")
    public ResponseEntity<?> getAllFolder() {
        return ResponseEntity.ok(new DataResponse<>(folderService.getAllFolders()));
    }

}
