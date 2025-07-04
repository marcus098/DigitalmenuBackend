package com.modules.mainapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication(scanBasePackages = {
		"com.modules.ingredientmodule",
		"com.modules.productmodule",
		"com.modules.authmodule",
		"com.modules.cardmodule",
		"com.modules.categorymodule",
		"com.modules.filemodule",
		"com.modules.ordermodule",
		"com.modules.stylemodule",
		"com.modules.tablemodule",
		"com.modules.waitermodule",
		"com.modules.servletconfiguration",
		"com.modules.servletconfiguration.jpa",
		"com.modules.common",
		"com.modules.mainapp"
})
@EnableJpaRepositories(basePackages = {
		"com.modules.ingredientmodule.repository",
		"com.modules.categorymodule.repository",
		"com.modules.authmodule.repository",
		"com.modules.stylemodule.repository",
		"com.modules.cardmodule.repository",
		"com.modules.waitermodule.repository",
		"com.modules.tablemodule.repository",
		"com.modules.filemodule.repository",
		"com.modules.productmodule.repository",
})
@EnableMongoRepositories(basePackages = {
		"com.modules.ingredientmodule.repository.mongo",
		"com.modules.categorymodule.repository.mongo",
		//"com.modules.stylemodule.repository.mongo",
		//"com.modules.cardmodule.repository.mongo",
		"com.modules.waitermodule.repository.mongo",
		"com.modules.ordermodule.repository",
		"com.modules.tablemodule.repository.mongo",
		"com.modules.productmodule.repository.mongo",
})
public class MainAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(MainAppApplication.class, args);
	}

}
