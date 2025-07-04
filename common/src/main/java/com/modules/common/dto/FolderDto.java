package com.modules.common.dto;

public class FolderDto {
    private Long id;
    private String name;
    private String url;

    public FolderDto() {}

    public FolderDto(Long id, String name, String url) {
        this.id = id;
        this.name = name;
        this.url = url;
    }

    public FolderDto(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return "FolderDto{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}
