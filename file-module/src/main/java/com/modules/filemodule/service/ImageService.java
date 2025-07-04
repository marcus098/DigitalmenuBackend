package com.modules.filemodule.service;

import com.modules.categorymodule.repository.CategoryRepository;
import com.modules.common.finders.ProductUtils;
import com.modules.common.logs.applicationlog.ApplicationLog;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.enums.TypeEntity;
import com.modules.common.utilities.Utilities;
import com.modules.filemodule.model.ImageJpa;
import com.modules.filemodule.repository.ImageRepository;
import io.minio.*;
import io.minio.errors.MinioException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ImageService {
    @Autowired
    private ImageRepository imageRepository;
    @Value("${spring.application.imagesPath}")
    private String imagesPath;
    @Value("${spring.application.deletedImagesPath}")
    private String deletedImagesPath;
    @Value("${spring.application.separator}")
    private String separator;
    @Value("${bucket.images.name}")
    private String BUCKET_NAME;
    @Value("${bucket.imagesdeleted.name}")
    private String BUCKET_DELETED_NAME;

    private MinioClient minioClient;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductUtils productUtils;
    @Autowired
    private Utilities utilities;

    public ImageService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Transactional
    public boolean deleteImage(long id, long idAgency, long idUser) {
        try {
            Optional<ImageJpa> image = imageRepository.findByIdAndIdAgencyAndDeleted(id, idAgency, false);
            if (image.isEmpty()) {
                throw new EntityNotFoundException("Image with id " + id + " not found");
            }
            ImageJpa imageToDelete = image.get();
            imageToDelete.setDeleted(true);
            imageToDelete.setDeletedAt(OffsetDateTime.now());
            imageRepository.save(imageToDelete);
            categoryRepository.updateImageToEmpty(imageToDelete.getName());
            productUtils.updateImageToEmpty(imageToDelete.getName()); // todo vedere se serve ricerca per idAgency e deleted
            return true;
        } catch (Exception e) {
            ErrorLog.logger.error("Errore eliminazione immagine ", e);
            return false;
        }
    }

    @Transactional
    public boolean deleteImage(String imageName, long idAgency, long idUser) {
        try {
            Optional<ImageJpa> image = imageRepository.findByNameAndIdAgencyAndDeleted(imageName, idAgency, false);
            if (image.isEmpty()) {
                throw new EntityNotFoundException("Image with name " + imageName + " not found");
            }
            ImageJpa imageToDelete = image.get();
            imageToDelete.setDeleted(true);
            imageToDelete.setDeletedAt(OffsetDateTime.now());
            utilities.moveFile(imagesPath + separator + imageName, deletedImagesPath + separator + imageName);
            imageRepository.save(imageToDelete);
            categoryRepository.updateImageToEmpty(imageName);
            productUtils.updateImageToEmpty(imageName);
            return true;
        } catch (Exception e) {
            ErrorLog.logger.error("Errore eliminazione immagine ", e);
            return false;
        }
    }

    public boolean deleteImageWithBucket(String img, long idAgency, long idUser) {
        try {
            Optional<ImageJpa> image = imageRepository.findByNameAndIdAgencyAndDeleted(img, idAgency, false);
            if (image.isEmpty()) {
                throw new EntityNotFoundException("Image with name " + img + " not found");
            }
            ImageJpa imageToDelete = image.get();
            String imageName = img;

            // Aggiorna lo stato dell'immagine nel database
            imageToDelete.setDeleted(true);
            imageToDelete.setDeletedAt(OffsetDateTime.now());
            imageRepository.save(imageToDelete);

            productUtils.updateImageToEmpty(imageToDelete.getName());
            categoryRepository.updateImageToEmpty(imageToDelete.getName());

            // Copia l'immagine nel bucket di eliminazione usando CopySource
            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(BUCKET_DELETED_NAME)
                            .object(imageName)
                            .source(
                                    CopySource.builder()
                                            .bucket(BUCKET_NAME)
                                            .object(imageName)
                                            .build()
                            )
                            .build()
            );

            // Rimuove l'immagine dal bucket principale
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(imageName)
                            .build()
            );
            return true;
        } catch (EntityNotFoundException e) {
            ErrorLog.logger.error("Image not found with img: " + img, e);
            return false;
        } catch (MinioException e) {
            ErrorLog.logger.error("Errore MinIO durante l'eliminazione dell'immagine", e);
            return false;
        } catch (Exception e) {
            ErrorLog.logger.error("Errore durante l'eliminazione dell'immagine", e);
            return false;
        }
    }

    public boolean deleteImageWithBucket(long id, long idAgency, long idUser) {
        try {
            Optional<ImageJpa> image = imageRepository.findByIdAndIdAgencyAndDeleted(id, idAgency, false);
            if (image.isEmpty()) {
                throw new EntityNotFoundException("Image with id " + id + " not found");
            }
            ImageJpa imageToDelete = image.get();
            String imageName = imageToDelete.getName();

            // Aggiorna lo stato dell'immagine nel database
            imageToDelete.setDeleted(true);
            imageToDelete.setDeletedAt(OffsetDateTime.now());
            imageRepository.save(imageToDelete);

            productUtils.updateImageToEmpty(imageToDelete.getName());
            categoryRepository.updateImageToEmpty(imageToDelete.getName());

            // Copia l'immagine nel bucket di eliminazione usando CopySource
            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(BUCKET_DELETED_NAME)
                            .object(imageName)
                            .source(
                                    CopySource.builder()
                                            .bucket(BUCKET_NAME)
                                            .object(imageName)
                                            .build()
                            )
                            .build()
            );

            // Rimuove l'immagine dal bucket principale
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(imageName)
                            .build()
            );
            return true;
        } catch (EntityNotFoundException e) {
            ErrorLog.logger.error("Image not found with id: " + id, e);
            return false;
        } catch (MinioException e) {
            ErrorLog.logger.error("Errore MinIO durante l'eliminazione dell'immagine", e);
            return false;
        } catch (Exception e) {
            ErrorLog.logger.error("Errore durante l'eliminazione dell'immagine", e);
            return false;
        }
    }

    @Transactional
    public String uploadImage(MultipartFile file, long idAgency, long idUser) {
        boolean checkSaved = false;
        String path = "";
        try {
            if (file != null && Objects.requireNonNull(file.getContentType()).startsWith("image")) {
                try {

                    //String ext = file.getContentType().split("/")[1];
                    UUID uuid = UUID.randomUUID();
                    byte[] optimizedImage = utilities.optimizeImage(file);
                    if (optimizedImage == null)
                        throw new Exception("Errore ottimizzazione immagine");
                    String ext = "webp";
                    String imageName = uuid.toString() + "." + ext;
                    ImageJpa image = new ImageJpa(imageName, idAgency);
                    File directory = new File(imagesPath.concat(separator).concat(idAgency + "").concat(separator));
                    boolean flag = utilities.createDirectoryIfNotExists(directory.getAbsolutePath());
                    if (!flag)
                        throw new Exception("Errore creazione directory per immagine");
                    File finalImage = new File(directory.getAbsolutePath(), imageName);
                    FileUtils.writeByteArrayToFile(finalImage, optimizedImage);
                    checkSaved = true;
                    path = directory.getAbsolutePath() + separator + imageName;
                    imageRepository.save(image);
                    List<TypeEntity> entities = new ArrayList<>();
                    entities.add(TypeEntity.IMAGE);
                    //Utilities.updateType(entities);
                } catch (Exception e) {
                    ErrorLog.logger.error("Errore con immagine", e);
                    if (checkSaved) {
                        utilities.deleteFile(path);
                    }
                }
            }

        } catch (EntityNotFoundException e) {
            ErrorLog.logger.error("Immagine non trovata", e);
        } catch (Exception e) {
            // Gestione degli errori generici
            ErrorLog.logger.error("Errore con immagine", e);
        }
        return "";
    }

    @Transactional
    public String uploadImageWithBucket(MultipartFile file, long idAgency, long idUser, String folder) {
        boolean checkSaved = false;
        String imagePath = "";
        String imageName = null;

        try {
            if (file != null && file.getContentType() != null && file.getContentType().startsWith("image")) {
                UUID uuid = UUID.randomUUID();
                byte[] optimizedImage = utilities.optimizeImage(file);
                if (optimizedImage == null) {
                    throw new Exception("Errore ottimizzazione immagine");
                }

                String ext = "webp"; // Estensione predefinita
                imageName = uuid.toString() + "." + ext;

                // Creazione oggetto Image per DB
                ImageJpa image = new ImageJpa(imageName, idAgency);

                // Caricamento nel bucket MinIO
                InputStream imageInputStream = new java.io.ByteArrayInputStream(optimizedImage);
                try {
                    minioClient.putObject(
                            PutObjectArgs.builder()
                                    .bucket(BUCKET_NAME)
                                    .object(folder.equals("cards") ? folder + "/" + imageName : idAgency + "/img/" + imageName)
                                    .stream(imageInputStream, optimizedImage.length, -1)
                                    .contentType("image/webp")
                                    .build());
                    checkSaved = true;
                    imagePath = BUCKET_NAME + "/" + idAgency + "/" + folder + "/" + imageName;
                } catch (MinioException e) {
                    ErrorLog.logger.error("Errore durante l'upload su MinIO", e);
                    return null;
                }

                // Tentativo di salvataggio nel database
                imageRepository.save(image);
                List<TypeEntity> entities = new ArrayList<>();
                entities.add(TypeEntity.IMAGE);
                //utilities.updateType(entities);

            }

        } catch (EntityNotFoundException e) {
            ErrorLog.logger.error("Immagine non trovata", e);
            return null;
        } catch (Exception e) {
            // Gestione degli errori generici
            ErrorLog.logger.error("Errore con immagine", e);
            // Se il salvataggio nel DB fallisce, elimina l'immagine dal bucket MinIO
            if (imageName != null && checkSaved) {
                try {
                    minioClient.removeObject(
                            RemoveObjectArgs.builder()
                                    .bucket(BUCKET_NAME)
                                    .object(imageName)
                                    .build());
                    ApplicationLog.logger.info("Immagine eliminata dal bucket MinIO: {}", imageName);
                } catch (Exception ex) {
                    ErrorLog.logger.error("Errore durante l'eliminazione dell'immagine dal bucket MinIO", ex);
                }
            }
            return null;
        }

        return imagePath;
    }

}
