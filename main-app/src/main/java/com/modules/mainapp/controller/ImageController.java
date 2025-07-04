package com.modules.mainapp.controller;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.InputStream;

@RestController
@CrossOrigin
@RequestMapping("/api")
public class ImageController {
    @Autowired
    private MinioClient minioClient;

    @GetMapping("/images/{filename}")
    public ResponseEntity<byte[]> getImage(@PathVariable String filename) {
        try {
            InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket("testmenu") // o il tuo BUCKET_NAME
                            .object(filename)
                            .build()
            );

            byte[] bytes = stream.readAllBytes();
            stream.close();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("image", "webp"));
            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/public/images/{filename}")
    public ResponseEntity<byte[]> getImagePublic(@PathVariable String filename) {
        try {
            InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket("testmenu") // nome del bucket todo usare value
                            .object(filename)
                            .build()
            );

            byte[] bytes = stream.readAllBytes();
            stream.close();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("image", "webp")); // o rileva dinamicamente se vuoi
            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}
