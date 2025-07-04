package com.modules.common.dto;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class FileDto {
    private Long id;
    private Long parentFolder;
    private String fileName;
    private String fileType;
    private String createdAt;
    private String fileSize;

    public FileDto(Long id, Long parentFolder, String fileName, String fileType, String createdAt, double fileSize) {
        this.id = id;
        this.parentFolder = parentFolder;
        this.fileName = fileName;
        this.fileType = fileType;
        this.createdAt = createdAt;
        if (fileSize < 1024)
            this.fileSize = String.format(Locale.US, "%.2f", fileSize) + "byte";
        else if (fileSize < (1024 * 1024))
            this.fileSize = String.format(Locale.US, "%.2f", (fileSize / 1024)) + "KB";
        else if (fileSize < (1024 * 1024 * 1024))
            this.fileSize = String.format(Locale.US, "%.2f", (fileSize / 1024 / 1024)) + "MB";
        else
            this.fileSize = String.format(Locale.US, "%.2f", (fileSize / 1024 / 1024 / 1024)) + "GB";
    }

    public FileDto(Long id, Long parentFolder, String fileName, String fileType, String createdAt, String fileSize) {
        this.id = id;
        this.parentFolder = parentFolder;
        this.fileName = fileName;
        this.fileType = fileType;
        this.createdAt = createdAt;
        this.fileSize = fileSize;
    }

    public FileDto(Long id, Long parentFolder, String fileName, String fileType, OffsetDateTime createdAt, double fileSize) {
        this.id = id;
        this.parentFolder = parentFolder;
        this.fileName = fileName;
        this.fileType = fileType;
        this.createdAt = createdAt.format(DateTimeFormatter.ISO_DATE_TIME);
        if (fileSize < 1024)
            this.fileSize = String.format(Locale.US, "%.2f", fileSize) + "byte";
        else if (fileSize < (1024 * 1024))
            this.fileSize = String.format(Locale.US, "%.2f", (fileSize / 1024)) + "KB";
        else if (fileSize < (1024 * 1024 * 1024))
            this.fileSize = String.format(Locale.US, "%.2f", (fileSize / 1024 / 1024)) + "MB";
        else
            this.fileSize = String.format(Locale.US, "%.2f", (fileSize / 1024 / 1024 / 1024)) + "GB";
    }

    public FileDto(Long id, Long parentFolder, String fileName, String fileType, OffsetDateTime createdAt, String fileSize) {
        this.id = id;
        this.parentFolder = parentFolder;
        this.fileName = fileName;
        this.fileType = fileType;
        this.createdAt = createdAt.format(DateTimeFormatter.ISO_DATE_TIME);
        this.fileSize = fileSize;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getParentFolder() {
        return parentFolder;
    }

    public void setParentFolder(Long parentFolder) {
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

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getFileSize() {
        return fileSize;
    }

    public void setFileSize(String fileSize) {
        this.fileSize = fileSize;
    }

    @Override
    public String toString() {
        return "FileDto{" +
                "id=" + id +
                ", parentFolder=" + parentFolder +
                ", fileName='" + fileName + '\'' +
                ", fileType='" + fileType + '\'' +
                ", createdAt=" + createdAt +
                ", fileSize=" + fileSize +
                '}';
    }
}
