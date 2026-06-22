package com.modules.filemodule.service;

import com.modules.categorymodule.repository.CategoryRepository;
import com.modules.common.finders.ProductUtils;
import com.modules.common.logs.applicationlog.ApplicationLog;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.enums.TypeEntity;
import com.modules.common.utilities.Utilities;
import com.modules.filemodule.model.ImageJpa;
import com.modules.filemodule.repository.ImageRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.File;
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

    private final S3Client s3Client;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductUtils productUtils;
    @Autowired
    private Utilities utilities;

    public ImageService(S3Client s3Client) {
        this.s3Client = s3Client;
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
            productUtils.updateImageToEmpty(imageToDelete.getName());
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

            imageToDelete.setDeleted(true);
            imageToDelete.setDeletedAt(OffsetDateTime.now());
            imageRepository.save(imageToDelete);

            productUtils.updateImageToEmpty(imageToDelete.getName());
            categoryRepository.updateImageToEmpty(imageToDelete.getName());

            s3Client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(BUCKET_NAME)
                    .sourceKey(img)
                    .destinationBucket(BUCKET_DELETED_NAME)
                    .destinationKey(img)
                    .build());

            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(img)
                    .build());

            return true;
        } catch (EntityNotFoundException e) {
            ErrorLog.logger.error("Image not found with img: " + img, e);
            return false;
        } catch (S3Exception e) {
            ErrorLog.logger.error("Errore S3 durante l'eliminazione dell'immagine", e);
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

            imageToDelete.setDeleted(true);
            imageToDelete.setDeletedAt(OffsetDateTime.now());
            imageRepository.save(imageToDelete);

            productUtils.updateImageToEmpty(imageToDelete.getName());
            categoryRepository.updateImageToEmpty(imageToDelete.getName());

            s3Client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(BUCKET_NAME)
                    .sourceKey(imageName)
                    .destinationBucket(BUCKET_DELETED_NAME)
                    .destinationKey(imageName)
                    .build());

            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(imageName)
                    .build());

            return true;
        } catch (EntityNotFoundException e) {
            ErrorLog.logger.error("Image not found with id: " + id, e);
            return false;
        } catch (S3Exception e) {
            ErrorLog.logger.error("Errore S3 durante l'eliminazione dell'immagine", e);
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
            ErrorLog.logger.error("Errore con immagine", e);
        }
        return "";
    }

    @Transactional
    public String uploadImageWithBucket(MultipartFile file, long idAgency, long idUser, String folder) {
        boolean checkSaved = false;
        String imagePath = "";
        String imageName = null;
        String objectName = null;

        try {
            if (file != null && file.getContentType() != null && file.getContentType().startsWith("image")) {
                UUID uuid = UUID.randomUUID();
                byte[] optimizedImage = utilities.optimizeImage(file);
                if (optimizedImage == null) {
                    throw new Exception("Errore ottimizzazione immagine");
                }

                imageName = uuid.toString() + ".webp";
                objectName = folder.equals("cards") ? folder + "/" + imageName : idAgency + "/img/" + imageName;

                ImageJpa image = new ImageJpa(imageName, idAgency);

                s3Client.putObject(
                        PutObjectRequest.builder()
                                .bucket(BUCKET_NAME)
                                .key(objectName)
                                .contentType("image/webp")
                                .build(),
                        RequestBody.fromBytes(optimizedImage));
                checkSaved = true;
                imagePath = BUCKET_NAME + "/" + idAgency + "/" + folder + "/" + imageName;

                imageRepository.save(image);
                List<TypeEntity> entities = new ArrayList<>();
                entities.add(TypeEntity.IMAGE);
            }
        } catch (EntityNotFoundException e) {
            ErrorLog.logger.error("Immagine non trovata", e);
            return null;
        } catch (Exception e) {
            ErrorLog.logger.error("Errore con immagine", e);
            if (objectName != null && checkSaved) {
                try {
                    s3Client.deleteObject(DeleteObjectRequest.builder()
                            .bucket(BUCKET_NAME)
                            .key(objectName)
                            .build());
                    ApplicationLog.logger.info("Immagine eliminata dal bucket S3: {}", objectName);
                } catch (Exception ex) {
                    ErrorLog.logger.error("Errore durante l'eliminazione dell'immagine dal bucket S3", ex);
                }
            }
            return null;
        }

        return imagePath;
    }
}
