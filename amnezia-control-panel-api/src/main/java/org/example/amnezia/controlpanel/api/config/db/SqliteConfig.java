package org.example.amnezia.controlpanel.api.config.db;

import static org.example.amnezia.controlpanel.api.config.db.DatabasePins.SQLITE_PREFIX;

import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

@Configuration
public class SqliteConfig {

  private static final String PROPERTY_PREFIX = "panel.api.datasource." + SQLITE_PREFIX;
  public static final String PROPERTY_HIKARI_PREFIX = PROPERTY_PREFIX + ".hikari";

  @Bean(DatabasePins.SQLITE_DATASOURCE_PROPERTIES_BEAN)
  @ConfigurationProperties(PROPERTY_PREFIX)
  public DataSourceProperties dataSourceProperties() {
    return new DataSourceProperties();
  }

  @Bean(DatabasePins.SQLITE_DATASOURCE_BEAN)
  @ConfigurationProperties(PROPERTY_HIKARI_PREFIX)
  public DataSource dataSource(
      @Qualifier(DatabasePins.SQLITE_DATASOURCE_PROPERTIES_BEAN)
          DataSourceProperties dataSourceProperties) {
    return dataSourceProperties.initializeDataSourceBuilder().build();
  }

  @Bean(DatabasePins.SQLITE_NAMED_JDBC_TEMPLATE_BEAN)
  public NamedParameterJdbcTemplate namedParameterJdbcTemplate(
      org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
    return new NamedParameterJdbcTemplate(jdbcTemplate);
  }

  @Bean(DatabasePins.SQLITE_TX_MANAGER_BEAN)
  public DataSourceTransactionManager transactionManager(
      @Qualifier(DatabasePins.SQLITE_DATASOURCE_BEAN) DataSource dataSource) {
    return new DataSourceTransactionManager(dataSource);
  }

  @Bean(DatabasePins.SQLITE_LIQUIBASE_BEAN)
  public SpringLiquibase liquibase(
      @Qualifier(DatabasePins.SQLITE_DATASOURCE_BEAN) DataSource dataSource,
      @Value("${spring.liquibase.change-log}") String changeLog) {
    SpringLiquibase liquibase = new SpringLiquibase();
    liquibase.setDataSource(dataSource);
    liquibase.setChangeLog(changeLog);
    return liquibase;
  }
}
