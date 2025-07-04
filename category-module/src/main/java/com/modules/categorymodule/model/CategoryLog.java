package com.modules.categorymodule.model;

import com.modules.common.dto.CategoryDto;
import com.modules.common.model.enums.LogOperation;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Setter
@Getter
@Document(collation = "categoryLog")
public class CategoryLog {
    @Id
    private String id;

    private LogOperation operation;
    private CategoryDto oldCategory;
    private CategoryDto newCategory;
    private String note;
    private long idUser;
    private long idAgency;
    private LocalDateTime loggedAt;

    public CategoryLog() {

    }

    // modifico una categoria
    public CategoryLog(LogOperation logOperation, CategoryDto oldCategory, CategoryDto newCategory, String note, long idUser, long idAgency) {
        this.operation = logOperation;
        this.oldCategory = oldCategory;
        this.newCategory = newCategory;
        this.note = note;
        this.idUser = idUser;
        this.idAgency = idAgency;
        this.loggedAt = LocalDateTime.now();
    }

    // aggiungo o elimino una categoria
    public CategoryLog(LogOperation logOperation, CategoryDto category, String note, long idUser, long idAgency, boolean isNew) {
        this.operation = logOperation;
        if (isNew)
            this.newCategory = category;
        else
            this.oldCategory = category;
        this.note = note;
        this.idUser = idUser;
        this.idAgency = idAgency;
        this.loggedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "CategoryLog{" +
                "id='" + id + '\'' +
                ", operation=" + operation +
                ", oldCategory=" + oldCategory +
                ", newCategory=" + newCategory +
                ", note='" + note + '\'' +
                ", idUser=" + idUser +
                ", idAgency=" + idAgency +
                ", loggedAt=" + loggedAt +
                '}';
    }
}
