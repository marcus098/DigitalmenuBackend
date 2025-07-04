package com.modules.common.finders;

import org.springframework.web.multipart.MultipartFile;

public interface FileUtils {
    String uploadImageWithBucket(MultipartFile file, long idAgency, long idUser, String folder);

}
