package com.modules.filemodule.service;

import com.modules.common.dto.FolderDto;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.filemodule.model.FileJpa;
import com.modules.filemodule.model.FolderJpa;
import com.modules.filemodule.repository.FileRepository;
import com.modules.filemodule.repository.FolderRepository;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FolderService {
    @Autowired
    private FolderRepository folderRepository;
    @Autowired
    private AuthenticatedUserProvider authUserProvider;
    @Autowired
    private FileRepository fileRepository;
    @Autowired
    private FileService fileService;
    @Value("${spring.application.separator}")
    private String separator;
    @Value("${bucket.filemanager.name}")
    private String BUCKET_NAME;
    @Value("${bucket.filemanagerdeleted.name}")
    private String BUCKET_DELETED_NAME;

    @Transactional
    public boolean deleteFolder(long id, boolean force) {
        long idAgency = authUserProvider.getAgencyId();
        long idUser = authUserProvider.getUserId();

        FolderJpa folderJpa = folderRepository.findByIdAndIdAgencyAndDeleted(id, idAgency, false).orElseThrow();

        List<FileJpa> fileJpas = fileRepository.findAllByIdAgencyAndDeletedAndParentFolder(idAgency, false, id);
        if (!fileJpas.isEmpty()) {
            if (!force)
                return false;
            for (FileJpa fileJpa : fileJpas) {
                fileJpa.setDeleted(true);
                fileJpa.setDeletedAt(OffsetDateTime.now());
                try{
                    boolean move = fileService.moveFromBucket(BUCKET_NAME.concat(separator).concat(fileJpa.getUrl()), BUCKET_DELETED_NAME.concat(separator).concat(fileJpa.getUrl()));
                    if(!move)
                        ErrorLog.logger.warn("Errore eliminazione file dal bucket - false");
                }catch (Exception e){
                    ErrorLog.logger.warn("Errore eliminazione file dal bucket ", e);
                }
            }
            fileRepository.saveAll(fileJpas);
        }
        // todo log
        folderJpa.setDeleted(true);
        folderJpa.setDeletedAt(OffsetDateTime.now());
        folderRepository.save(folderJpa);

        return true;
    }

    public FolderDto addFolder(String name) {
        long idAgency = authUserProvider.getAgencyId();
        long idUser = authUserProvider.getUserId();
        // todo log
        FolderJpa folderJpa = new FolderJpa(name, OffsetDateTime.now(), null, false, idAgency);
        folderJpa = folderRepository.save(folderJpa);
        return new FolderDto(folderJpa.getId(), folderJpa.getName(), "");
    }

    public FolderDto renameFolder(long id, String newName) {
        long idAgency = authUserProvider.getAgencyId();
        long idUser = authUserProvider.getUserId();
        // todo log
        FolderJpa folderJpa = folderRepository.findByIdAndIdAgencyAndDeleted(id, idAgency, false).orElseThrow();
        folderJpa.setName(newName);
        folderJpa = folderRepository.save(folderJpa);
        return new FolderDto(folderJpa.getId(), folderJpa.getName(), "");
    }

    public List<FolderDto> getAllFolders(){
        long idAgency = authUserProvider.getAgencyId();
        return folderRepository.findAllByIdAgencyAndDeleted(idAgency, false).stream().map(folder -> new FolderDto(folder.getId(), folder.getName(), "")).collect(Collectors.toList());
    }

}
