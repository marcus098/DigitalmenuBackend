package com.modules.common.dto;

import com.fasterxml.jackson.annotation.JsonView;
import com.modules.common.model.db.Image;

public class ImageDto {
    private Long id;
    private String path;

    @JsonView(Views.Updating.class)
    private String sessionUpdating;
    @JsonView(Views.Updating.class)
    private String changeType;

    public ImageDto() {
    }

    public ImageDto(String path) {
        this.path = path;
    }

    public ImageDto(Image image) {
        this.path = image.getName();
    }


    public ImageDto(String path, String sessionUpdating, String changeType) {
        this.changeType = changeType;
        this.sessionUpdating = sessionUpdating;
        this.path = path;
    }

    public ImageDto(Image image, String sessionUpdating, String changeType) {
        this.changeType = changeType;
        this.sessionUpdating = sessionUpdating;
        this.path = image.getName();
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getSessionUpdating() {
        return sessionUpdating;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public Long getId() {
        return id;
    }

    public void setSessionUpdating(String sessionUpdating) {
        this.sessionUpdating = sessionUpdating;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "ImageDto{" +
                "id=" + id +
                ", path='" + path + '\'' +
                ", sessionUpdating='" + sessionUpdating + '\'' +
                ", changeType='" + changeType + '\'' +
                '}';
    }
}
