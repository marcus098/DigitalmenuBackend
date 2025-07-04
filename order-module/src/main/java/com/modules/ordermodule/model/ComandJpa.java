package com.modules.ordermodule.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.modules.common.model.Comand;
import com.modules.common.model.ComandType;
import com.modules.common.model.Order;
import com.modules.common.model.enums.ComandStatus;
import com.modules.common.model.enums.ComandWaiterType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@Document(collection = "comand")
public class ComandJpa extends Comand {
    private Long idTable;
    private String name;
    private Long idWaiter;
    private String time;
    private String phone;
    private String address;
    private ComandWaiterType comandWaiterType;

    public ComandJpa() {

    }

    @Id
    @Override
    public String getId() {
        return super.getId();
    }

    public ComandJpa(Long idAgency, ComandType comandType) {
        super(idAgency, comandType);
    }

    public ComandJpa(Long idAgency, List<Order> orders, ComandStatus status, ComandType comandType) {
        super(idAgency, orders, status, comandType);
    }
    public ComandJpa(Long idAgency, ComandType comandType, String tableSessionId) {
        super(idAgency, comandType, tableSessionId);
    }

    public ComandJpa(Long idAgency, List<Order> orders, ComandStatus status, ComandType comandType, String tableSessionId) {
        super(idAgency, orders, status, comandType, tableSessionId);
    }

}
