package com.modules.webfluxmodule.configurations;

import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider;
import io.r2dbc.postgresql.client.SSLMode;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabaseConfig {

    @Value("${postgresql.driver}")
    private String driver;

    @Value("${postgresql.host}")
    private String host;

    @Value("${postgresql.password}")
    private String password;

    @Value("${postgresql.database}")
    private String database;

    @Value("${postgresql.port}")
    private int port;

    @Value("${postgresql.user}")
    private String user;

    @Value("${postgresql.ssl}")
    private boolean ssl;

    @Bean
    public ConnectionFactory connectionFactory() {
        System.out.println();
        return ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, driver)
                .option(ConnectionFactoryOptions.HOST, host)
                .option(ConnectionFactoryOptions.PORT, port)
                .option(ConnectionFactoryOptions.DATABASE, database)
                .option(ConnectionFactoryOptions.USER, user) // Sostituisci con il tuo utente
                .option(ConnectionFactoryOptions.PASSWORD, password) // Sostituisci con la tua password
                .option(ConnectionFactoryOptions.SSL, ssl) // Disabilita SSL
                .option(PostgresqlConnectionFactoryProvider.SSL_MODE, SSLMode.DISABLE) // Disabilita SSL
                .build());
    }
}
