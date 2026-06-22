package com.modules.filemodule.service;

import com.modules.common.dto.FileDto;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.filemodule.model.FileJpa;
import com.modules.filemodule.repository.FileRepository;
import com.modules.filemodule.requests.AddFile;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FileService {
    @Autowired
    private FileRepository fileRepository;
    @Autowired
    private AuthenticatedUserProvider authUserProvider;
    @Value("${spring.application.separator}")
    private String separator;
    @Value("${bucket.filemanager.name}")
    private String BUCKET_NAME;
    @Value("${bucket.filemanagerdeleted.name}")
    private String BUCKET_DELETED_NAME;
    @Autowired
    private S3Client s3Client;
    @Autowired
    private S3Presigner s3Presigner;

    public String downloadFile(long id) {
        long idAgency = authUserProvider.getAgencyId();
        long idUser = authUserProvider.getUserId();
        ErrorLog.logger.info("File {} download requested by user {} (agency {})", id, idUser, idAgency);
        FileJpa fileJpa = fileRepository.findByIdAndIdAgencyAndDeleted(id, idAgency, false).orElseThrow();
        return getPresignedUrlForDownload(BUCKET_NAME, fileJpa.getUrl(), Duration.ofMinutes(2L)).orElseThrow();
    }

    public FileDto addFile(AddFile addFile, MultipartFile multipartFile) {
        long idAgency = authUserProvider.getAgencyId();
        long idUser = authUserProvider.getUserId();
        ErrorLog.logger.info("File upload started by user {} (agency {})", idUser, idAgency);
        double size = multipartFile.getSize();
        String fileName = addFile.getFileName() != null && !addFile.getFileName().trim().isEmpty()
                ? addFile.getFileName() : multipartFile.getOriginalFilename();
        String fileType = StringUtils.getFilenameExtension(fileName);
        String url = saveFile(multipartFile, BUCKET_NAME, idAgency + "").orElseThrow();
        FileJpa fileJpa = new FileJpa(addFile.getParentFolder(), fileName, fileType, OffsetDateTime.now(), size, false, idAgency, url);
        fileJpa = fileRepository.save(fileJpa);
        return new FileDto(fileJpa.getId(), fileJpa.getParentFolder(), fileJpa.getFileName(), fileJpa.getFileType(), fileJpa.getCreatedAt(), fileJpa.getFileSize());
    }

    @Transactional
    public boolean deleteFile(long id) {
        long idAgency = authUserProvider.getAgencyId();
        long idUser = authUserProvider.getUserId();
        ErrorLog.logger.info("File {} deleted by user {} (agency {})", id, idUser, idAgency);
        FileJpa fileJpa = fileRepository.findByIdAndIdAgencyAndDeleted(id, idAgency, false).orElseThrow();
        fileJpa.setDeleted(true);
        fileRepository.save(fileJpa);
        try {
            boolean move = moveFromBucket(
                    BUCKET_NAME.concat(separator).concat(fileJpa.getFileName()),
                    BUCKET_DELETED_NAME.concat(separator).concat(fileJpa.getFileName()));
            if (!move)
                ErrorLog.logger.warn("Errore spostamento bucket ");
        } catch (Exception e) {
            ErrorLog.logger.warn("Errore eliminazione file dal bucket ", e);
        }
        return true;
    }

    public FileDto renameFile(long id, String newName) {
        long idAgency = authUserProvider.getAgencyId();
        long idUser = authUserProvider.getUserId();
        ErrorLog.logger.info("File {} renamed to '{}' by user {} (agency {})", id, newName, idUser, idAgency);
        FileJpa fileJpa = fileRepository.findByIdAndIdAgencyAndDeleted(id, idAgency, false).orElseThrow();
        fileJpa.setFileName(newName);
        fileJpa = fileRepository.save(fileJpa);
        return new FileDto(fileJpa.getId(), fileJpa.getParentFolder(), fileJpa.getFileName(),
                fileJpa.getFileType(), fileJpa.getCreatedAt(), fileJpa.getFileSize());
    }

    public List<FileDto> getFilesByFolderId(long id) {
        long idAgency = authUserProvider.getAgencyId();
        return fileRepository.findAllByIdAgencyAndDeletedAndParentFolder(idAgency, false, id)
                .stream()
                .map(f -> new FileDto(f.getId(), f.getParentFolder(), f.getFileName(),
                        f.getFileType(), f.getCreatedAt(), f.getFileSize()))
                .collect(Collectors.toList());
    }

    private boolean deleteFromBucket(String from) {
        if (from == null || from.isBlank() || !from.contains("/")) {
            ErrorLog.logger.error("Il percorso 'from' non è valido.");
            return false;
        }
        try {
            String bucketName = from.substring(0, from.indexOf('/'));
            String objectName = from.substring(from.indexOf('/') + 1);
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build());
            return true;
        } catch (Exception e) {
            ErrorLog.logger.error("Errore durante la cancellazione dell'oggetto da S3: " + e.getMessage(), e);
            return false;
        }
    }

    public boolean moveFromBucket(String from, String to) {
        if (from == null || from.isBlank() || !from.contains(separator) ||
                to == null || to.isBlank() || !to.contains(separator)) {
            ErrorLog.logger.error("I percorsi 'from' e 'to' non sono validi.");
            return false;
        }
        try {
            String sourceBucket = from.substring(0, from.indexOf(separator));
            String sourceObject = from.substring(from.indexOf(separator) + 1);
            String destBucket = to.substring(0, to.indexOf(separator));
            String destObject = to.substring(to.indexOf(separator) + 1);

            s3Client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(sourceBucket)
                    .sourceKey(sourceObject)
                    .destinationBucket(destBucket)
                    .destinationKey(destObject)
                    .build());

            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(sourceBucket)
                    .key(sourceObject)
                    .build());

            return true;
        } catch (Exception e) {
            ErrorLog.logger.error("Errore durante lo spostamento dell'oggetto in S3: " + e.getMessage(), e);
            return false;
        }
    }

    public Optional<String> saveFile(MultipartFile file, String bucketName, String... pathSegments) {
        if (file == null || file.isEmpty()) {
            ErrorLog.logger.warn("Tentativo di upload di un file nullo o vuoto.");
            return Optional.empty();
        }
        try {
            String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
            String uniqueFilename = UUID.randomUUID().toString() + (extension != null ? "." + extension : "");
            String path = String.join("/", pathSegments);
            String objectName = (path.isEmpty() ? "" : path + "/") + uniqueFilename;

            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectName)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            return Optional.of(objectName);
        } catch (Exception e) {
            ErrorLog.logger.error("Errore durante l'upload del file su S3", e);
            return Optional.empty();
        }
    }

    public Optional<String> getPresignedUrlForDownload(String bucketName, String objectName, Duration expiry) {
        if (bucketName == null || bucketName.isBlank() || objectName == null || objectName.isBlank()) {
            ErrorLog.logger.warn("Tentativo di generare un URL pre-firmato con bucket o object name non validi.");
            return Optional.empty();
        }
        try {
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(expiry)
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectName)
                            .build())
                    .build();
            String url = s3Presigner.presignGetObject(presignRequest).url().toString();
            return Optional.of(url);
        } catch (Exception e) {
            ErrorLog.logger.error("Errore durante la generazione dell'URL pre-firmato per l'oggetto {}", objectName, e);
            return Optional.empty();
        }
    }
}
