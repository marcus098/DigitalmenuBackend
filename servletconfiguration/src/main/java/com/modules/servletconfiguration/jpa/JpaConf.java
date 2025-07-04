package com.modules.servletconfiguration.jpa;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;


@Configuration
@EntityScan(basePackages = {
        "com.modules.ingredientmodule.model",
        "com.modules.productmodule.model",
        "com.modules.stylemodule.models",
        "com.modules.cardmodule.models",
        "com.modules.waitermodule.model",
        "com.modules.authmodule.model",
        "com.modules.categorymodule.model",
        "com.modules.tablemodule.model",
        "com.modules.ordermodule.model",
        "com.modules.filemodule.model"
})
public class JpaConf {
    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;


    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan(
                "com.modules.ingredientmodule.model",
                "com.modules.productmodule.model",
                "com.modules.stylemodule.models",
                "com.modules.cardmodule.models",
                "com.modules.waitermodule.model",
                "com.modules.authmodule.model",
                "com.modules.categorymodule.model",
                "com.modules.tablemodule.model",
                "com.modules.ordermodule.model",
                "com.modules.filemodule.model"
        );
        factory.setJpaVendorAdapter(hibernateJpaVendorAdapter());
        factory.setPersistenceProviderClass(HibernatePersistenceProvider.class);
        return factory;
    }

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean
    public JpaVendorAdapter hibernateJpaVendorAdapter() {
        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        adapter.setGenerateDdl(true);
        adapter.setShowSql(true);
        return adapter;
    }

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName(driverClassName);
        return dataSource;
    }

}