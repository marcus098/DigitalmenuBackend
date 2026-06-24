package com.modules.webfluxmodule;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.modules.webfluxmodule",
        "com.modules.common",
})
public class WebfluxModuleApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(WebfluxModuleApplication.class);
        app.setWebApplicationType(WebApplicationType.REACTIVE);
        app.run(args);
    }

}
