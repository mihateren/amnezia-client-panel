package org.example.amnezia.controlpanel.api.config.db;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

import static org.example.amnezia.controlpanel.api.config.db.DatabaseBeans.POSTGRES_PREFIX;

@Configuration
public class PostgresConfig {

    private static final String PROPERTY_PREFIX = "panel.api.datasource" + POSTGRES_PREFIX;
    public static final String PROPERTY_HIKARI_PREFIX = PROPERTY_PREFIX + ".hikari";

    @Bean(DatabaseBeans.POSTGRES_DATASOURCE_PROPERTIES_BEAN)
    @ConfigurationProperties(PROPERTY_PREFIX)
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(DatabaseBeans.POSTGRES_DATASOURCE_BEAN)
    @ConfigurationProperties(PROPERTY_HIKARI_PREFIX)
    public DataSource dataSource(
            @Qualifier(DatabaseBeans.POSTGRES_DATASOURCE_PROPERTIES_BEAN) DataSourceProperties dataSourceProperties) {
        return dataSourceProperties.initializeDataSourceBuilder().build();
    }

    @Bean(DatabaseBeans.POSTGRES_NAMED_JDBC_TEMPLATE_BEAN)
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(
            @Qualifier(DatabaseBeans.POSTGRES_DATASOURCE_BEAN) DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean(DatabaseBeans.POSTGRES_TX_MANAGER_BEAN)
    public DataSourceTransactionManager transactionManager(
            @Qualifier(DatabaseBeans.POSTGRES_DATASOURCE_BEAN) DataSource dataSource
    ) {
        return new DataSourceTransactionManager(dataSource);
    }

}
