package com.modules.ordermodule.model;

import com.modules.common.model.ComandFromTable;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

@Setter
@Getter
@Document(collection = "comand")
public class ComandFromTableJpa extends ComandFromTable {

    public ComandFromTableJpa(long idAgency, long idTable, String userKey) {
        super(idAgency, idTable, userKey);
    }

    @Id
    @Override
    public String getId() {
        return super.getId();
    }
}
