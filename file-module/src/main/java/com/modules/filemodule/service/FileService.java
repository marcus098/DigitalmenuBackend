package com.modules.filemodule.service;

import com.google.zxing.NotFoundException;
import com.modules.common.dto.FileDto;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.filemodule.model.FileJpa;
import com.modules.filemodule.repository.FileRepository;
import com.modules.filemodule.repository.FolderRepository;
import com.modules.filemodule.repository.ImageRepository;
import com.modules.filemodule.requests.AddFile;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import io.minio.*;
import io.minio.http.Method;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
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
    private MinioClient minioClient;

    public String downloadFile(long id) {
        long idAgency = authUserProvider.getAgencyId();
        long idUser = authUserProvider.getUserId();
        // todo log
        FileJpa fileJpa = fileRepository.findByIdAndIdAgencyAndDeleted(id, idAgency, false).orElseThrow();

        return getPresignedUrlForDownload(BUCKET_NAME, fileJpa.getUrl(), Duration.ofMinutes(2L)).orElseThrow();
    }

    public FileDto addFile(AddFile addFile, MultipartFile multipartFile) {
        long idAgency = authUserProvider.getAgencyId();
        long idUser = authUserProvider.getUserId();
        //todo log
        double size = multipartFile.getSize();
        String fileName = addFile.getFileName() != null && !addFile.getFileName().trim().isEmpty() ? addFile.getFileName() : multipartFile.getOriginalFilename();
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
        // todo log
        FileJpa fileJpa = fileRepository.findByIdAndIdAgencyAndDeleted(id, idAgency, false).orElseThrow();
        fileJpa.setDeleted(true);
        fileRepository.save(fileJpa);
        try {
            boolean move = moveFromBucket(BUCKET_NAME.concat(separator).concat(fileJpa.getFileName()), BUCKET_DELETED_NAME.concat(separator).concat(fileJpa.getFileName()));
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
        // todo log
        FileJpa fileJpa = fileRepository.findByIdAndIdAgencyAndDeleted(id, idAgency, false).orElseThrow();
        fileJpa.setFileName(newName);
        fileJpa = fileRepository.save(fileJpa);
        return new FileDto(
                fileJpa.getId(),
                fileJpa.getParentFolder(),
                fileJpa.getFileName(),
                fileJpa.getFileType(),
                fileJpa.getCreatedAt(),
                fileJpa.getFileSize()
        );
    }

    public List<FileDto> getFilesByFolderId(long id) {
        long idAgency = authUserProvider.getAgencyId();

        return fileRepository.findAllByIdAgencyAndDeletedAndParentFolder(idAgency, false, id)
                .stream()
                .map(fileJpa -> new FileDto(fileJpa.getId(), fileJpa.getParentFolder(), fileJpa.getFileName(), fileJpa.getFileType(), fileJpa.getCreatedAt(), fileJpa.getFileSize()))
                .collect(Collectors.toList());
    }

    /**
     * Cancella un oggetto da un bucket MinIO.
     *
     * @param from Il percorso completo dell'oggetto, incluso il nome del bucket.
     *             Es: "mio-bucket/immagini/prodotto1.webp"
     * @return true se la cancellazione ha avuto successo, false altrimenti.
     */
    private boolean deleteFromBucket(String from) {
        if (from == null || from.isBlank() || !from.contains("/")) {
            System.err.println("Il percorso 'from' non è valido.");
            return false;
        }

        try {
            String bucketName = from.substring(0, from.indexOf('/'));
            String objectName = from.substring(from.indexOf('/') + 1);

            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build());

            return true;

        } catch (Exception e) {
            ErrorLog.logger.error("Errore durante la cancellazione dell'oggetto da MinIO: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Sposta un oggetto da una posizione a un'altra in MinIO.
     * L'operazione è una COPIA seguita da una CANCELLAZIONE.
     *
     * @param from Il percorso completo di origine, es: "mio-bucket/vecchio/path/oggetto.webp"
     * @param to   Il percorso completo di destinazione, es: "mio-bucket/nuovo/path/oggetto.webp"
     * @return true se lo spostamento ha avuto successo, false altrimenti.
     */
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

            // --- FASE 1: COPIA ---
            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(destBucket)
                            .object(destObject)
                            .source(
                                    CopySource.builder()
                                            .bucket(sourceBucket)
                                            .object(sourceObject)
                                            .build()
                            )
                            .build());

            // --- FASE 2: CANCELLAZIONE ---
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(sourceBucket)
                            .object(sourceObject)
                            .build());

            return true;

        } catch (Exception e) {
            ErrorLog.logger.error("Errore durante lo spostamento dell'oggetto in MinIO: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Salva un file in un bucket MinIO in un percorso specificato, generando un nome univoco.
     *
     * @param file         Il MultipartFile da caricare.
     * @param bucketName   Il nome del bucket di destinazione.
     * @param pathSegments I segmenti del percorso (le "sottocartelle") in cui salvare il file.
     * @return Un Optional contenente il percorso completo dell'oggetto salvato (senza il bucket),
     * o un Optional vuoto in caso di fallimento.
     */
    public Optional<String> saveFile(MultipartFile file, String bucketName, String... pathSegments) {
        // 1. Validazione dell'input
        if (file == null || file.isEmpty()) {
            ErrorLog.logger.warn("Tentativo di upload di un file nullo o vuoto.");
            return Optional.empty();
        }

        try {
            // 2. Creazione di un nome file sicuro e univoco
            String originalFilename = file.getOriginalFilename();
            String extension = StringUtils.getFilenameExtension(originalFilename);
            String uniqueFilename = UUID.randomUUID().toString() + (extension != null ? "." + extension : "");

            // 3. Costruzione del percorso completo dell'oggetto
            String path = String.join("/", pathSegments);
            String objectName = (path.isEmpty() ? "" : path + "/") + uniqueFilename;

            // 4. Ottenere lo stream di dati e le informazioni dal MultipartFile
            InputStream inputStream = file.getInputStream();
            long size = file.getSize();
            String contentType = file.getContentType();

            // 5. Eseguire l'upload su MinIO
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, size, -1) // -1 indica a MinIO di non dividere in parti
                            .contentType(contentType)
                            .build());

            // 6. Restituire il percorso completo dell'oggetto salvato
            return Optional.of(objectName);

        } catch (Exception e) {
            // 7. Gestione degli errori
            ErrorLog.logger.error("Errore durante l'upload del file su MinIO", e);
            return Optional.empty();
        }
    }

    /**
     * Genera un URL pre-firmato con scadenza per scaricare un oggetto da un bucket privato.
     *
     * @param bucketName Il nome del bucket in cui si trova l'oggetto.
     * @param objectName Il percorso completo (chiave) dell'oggetto nel bucket.
     * @param expiry     La durata di validità dell'URL (es. Duration.ofMinutes(5)).
     * @return Un Optional contenente l'URL pre-firmato, o un Optional vuoto in caso di errore.
     */
    public Optional<String> getPresignedUrlForDownload(String bucketName, String objectName, Duration expiry) {
        // 1. Validazione dell'input
        if (bucketName == null || bucketName.isBlank() || objectName == null || objectName.isBlank()) {
            ErrorLog.logger.warn("Tentativo di generare un URL pre-firmato con bucket o object name non validi.");
            return Optional.empty();
        }

        try {
            // 2. Prepara gli argomenti per la generazione dell'URL
            GetPresignedObjectUrlArgs args = GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET) // Il metodo HTTP per cui l'URL sarà valido (GET per il download)
                    .bucket(bucketName)
                    .object(objectName)
                    .expiry(300) // Imposta la durata di validità
                    .build();

            // 3. Genera l'URL
            String url = minioClient.getPresignedObjectUrl(args);

            return Optional.of(url);

        } catch (Exception e) {
            // 4. Gestione degli errori
            ErrorLog.logger.error("Errore durante la generazione dell'URL pre-firmato per l'oggetto {}", objectName, e);
            return Optional.empty();
        }
    }

}
