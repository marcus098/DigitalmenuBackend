package com.modules.filemodule.requests;

public class AddFile {
    private String fileName;
    private Long parentFolder;

    public AddFile() {}

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getParentFolder() {
        return parentFolder;
    }

    public void setParentFolder(Long parentFolder) {
        this.parentFolder = parentFolder;
    }

    @Override
    public String toString() {
        return "AddFile{" +
                "fileName='" + fileName + '\'' +
                ", parentFolder=" + parentFolder +
                '}';
    }
}
