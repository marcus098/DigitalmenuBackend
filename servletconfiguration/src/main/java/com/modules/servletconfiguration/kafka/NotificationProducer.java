package com.modules.servletconfiguration.kafka;

import com.modules.common.model.enums.TypeEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotificationProducer {
    @Autowired
    private CategoriesKafkaProducer categoryKafkaProducer;
    @Autowired
    private ProductsKafkaProducer productKafkaProducer;
    @Autowired
    private IngredientKafkaProducer ingredientKafkaProducer;
    @Autowired
    private TableKafkaProducer tableKafkaProducer;
    @Autowired
    private OrderKafkaProducer orderKafkaProducer;
    //@Autowired
    //private StyleKafkaProducer styleKafkaProducer;
    @Autowired
    private ImageKafkaProducer imageKafkaProducer;

    public void sendNotifications (List<TypeEntity> typeEntities) {
        for (TypeEntity typeEntity : typeEntities) {
            switch (typeEntity) {
                case TABLE -> tableKafkaProducer.sendUpdate("");
                case PRODUCT -> productKafkaProducer.sendUpdate("");
                case INGREDIENT -> ingredientKafkaProducer.sendUpdate("");
                case CATEGORY -> categoryKafkaProducer.sendUpdate("");
                case IMAGE -> imageKafkaProducer.sendUpdate("");
                case ORDER -> orderKafkaProducer.sendUpdate("");
                //case STYLE -> styleKafkaProducer.sendUpdate("");
            }
        }
    }
}
