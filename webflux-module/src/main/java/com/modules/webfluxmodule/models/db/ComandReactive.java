package com.modules.webfluxmodule.models.db;

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
public class ComandReactive extends Comand {
    private Long idTable;
    private String name;
    private Long idWaiter;
    private String time;
    private String phone;
    private String address;
    private ComandWaiterType comandWaiterType;

    public ComandReactive(){

    }

    @Id
    @Override
    public String getId() {
        return super.getId();
    }

    public ComandReactive(Long idAgency, ComandType comandType){
        super(idAgency, comandType);
    }

    public ComandReactive(Long idAgency, List<Order> orders, ComandStatus status, ComandType comandType) {
    super(idAgency, orders, status, comandType);
    }

    public ComandReactive(Long idAgency, ComandType comandType, String sessionId){
        super(idAgency, comandType, sessionId);
    }

    public ComandReactive(Long idAgency, List<Order> orders, ComandStatus status, ComandType comandType, String sessionId) {
        super(idAgency, orders, status, comandType, sessionId);
    }

}
