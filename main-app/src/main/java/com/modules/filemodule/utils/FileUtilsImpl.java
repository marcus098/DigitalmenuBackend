package com.modules.filemodule.utils;

import com.modules.common.finders.FileUtils;
import com.modules.filemodule.service.ImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class FileUtilsImpl implements FileUtils {
    @Autowired
    private ImageService imageService;

    @Override
    public String uploadImageWithBucket(MultipartFile file, long idAgency, long idUser, String folder) {
        return imageService.uploadImageWithBucket(file, idAgency, idUser, folder);
    }
}
