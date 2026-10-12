package com.modules.webfluxmodule;

import com.modules.common.utilities.BusinessTime;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.modules.webfluxmodule",
        "com.modules.common",
})
public class WebfluxModuleApplication {

    public static void main(String[] args) {
        // Stesso fuso del main-app: le LocalDateTime delle comande (Mongo, SSE, Kafka) sono ore italiane
        BusinessTime.useAsJvmDefault();
        SpringApplication app = new SpringApplication(WebfluxModuleApplication.class);
        app.setWebApplicationType(WebApplicationType.REACTIVE);
        app.run(args);
    }

}
