package com.modules.filemodule.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table
public class FileJpa {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    private long parentFolder;
    private String fileName;
    private String fileType;
    private OffsetDateTime createdAt;
    private OffsetDateTime deletedAt;
    private double fileSize;
    private boolean deleted;
    private long idAgency;
    private String url;

    public FileJpa() {

    }

    public FileJpa(long id, long parentFolder, String fileName, String fileType, OffsetDateTime createdAt, double fileSize, boolean deleted, long idAgency, String url) {
        this.id = id;
        this.url = url;
        this.parentFolder = parentFolder;
        this.fileName = fileName;
        this.fileType = fileType;
        this.createdAt = createdAt;
        this.fileSize = fileSize;
        this.deleted = deleted;
        this.idAgency = idAgency;
    }

    public FileJpa(long parentFolder, String fileName, String fileType, OffsetDateTime createdAt, double fileSize, boolean deleted, long idAgency, String url) {
        this.parentFolder = parentFolder;
        this.fileName = fileName;
        this.fileType = fileType;
        this.createdAt = createdAt;
        this.fileSize = fileSize;
        this.deleted = deleted;
        this.idAgency = idAgency;
        this.url = url;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public long getIdAgency() {
        return idAgency;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setIdAgency(long idAgency) {
        this.idAgency = idAgency;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getParentFolder() {
        return parentFolder;
    }

    public void setParentFolder(long parentFolder) {
        this.parentFolder = parentFolder;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public double getFileSize() {
        return fileSize;
    }

    public void setFileSize(double fileSize) {
        this.fileSize = fileSize;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    @Override
    public String toString() {
        return "FileJpa{" +
                "id=" + id +
                ", parentFolder=" + parentFolder +
                ", fileName='" + fileName + '\'' +
                ", fileType='" + fileType + '\'' +
                ", createdAt=" + createdAt +
                ", fileSize=" + fileSize +
                ", deleted=" + deleted +
                ", idAgency=" + idAgency +
                '}';
    }
}
