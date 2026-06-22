package com.modules.mainapp.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.InputStream;

@RestController
@CrossOrigin
@RequestMapping("/api")
public class ImageController {
    @Autowired
    private S3Client s3Client;

    @Value("${bucket.images.name}")
    private String BUCKET_NAME;

    @GetMapping("/images/{filename}")
    public ResponseEntity<byte[]> getImage(@PathVariable String filename) {
        try {
            InputStream stream = s3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(BUCKET_NAME)
                            .key(filename)
                            .build());
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
            InputStream stream = s3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(BUCKET_NAME)
                            .key(filename)
                            .build());
            byte[] bytes = stream.readAllBytes();
            stream.close();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("image", "webp"));
            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
