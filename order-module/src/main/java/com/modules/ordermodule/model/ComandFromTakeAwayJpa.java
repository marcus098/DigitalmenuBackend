package com.modules.ordermodule.model;

import com.modules.common.model.ComandFromTakeAway;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Setter
@Getter
@Document(collection = "comand")
public class ComandFromTakeAwayJpa extends ComandFromTakeAway {

    public ComandFromTakeAwayJpa(long idAgency, String userName, String userPhoneNumber, LocalDateTime dateDelivery, boolean payed) {
        super(idAgency, userName, userPhoneNumber, dateDelivery, payed);
    }

    @Id
    @Override
    public String getId() {
        return super.getId();
    }
}
