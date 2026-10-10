package org.example.amnezia.controlpanel.api.config.db;

import static org.example.amnezia.controlpanel.api.config.db.DatabaseBeans.SQLITE_PREFIX;

import io.micrometer.core.instrument.MeterRegistry;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.example.amnezia.controlpanel.api.monitoring.SqlQueryMetrics;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.support.SQLExceptionSubclassTranslator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class SqliteConfig {

  private static final String PROPERTY_PREFIX = "panel.api.datasource." + SQLITE_PREFIX;
  public static final String PROPERTY_HIKARI_PREFIX = PROPERTY_PREFIX + ".hikari";

  @Bean(DatabaseBeans.SQLITE_DATASOURCE_PROPERTIES_BEAN)
  @ConfigurationProperties(PROPERTY_PREFIX)
  public DataSourceProperties dataSourceProperties() {
    return new DataSourceProperties();
  }

  @Bean(DatabaseBeans.SQLITE_DATASOURCE_BEAN)
  @ConfigurationProperties(PROPERTY_HIKARI_PREFIX)
  public DataSource dataSource(
      @Qualifier(DatabaseBeans.SQLITE_DATASOURCE_PROPERTIES_BEAN)
          DataSourceProperties dataSourceProperties) {
    return dataSourceProperties.initializeDataSourceBuilder().build();
  }

  @Bean(DatabaseBeans.SQLITE_JDBC_TEMPLATE_BEAN)
  public JdbcTemplate jdbcTemplate(
      @Qualifier(DatabaseBeans.SQLITE_DATASOURCE_BEAN) DataSource dataSource) {
    var jdbc = new JdbcTemplate(dataSource);
    var fallback = new SQLExceptionSubclassTranslator();
    jdbc.setExceptionTranslator(
        (task, sql, exception) ->
            exception.getErrorCode() == 19
                ? new DataIntegrityViolationException("Database constraint violation")
                : fallback.translate(task, sql, exception));
    return jdbc;
  }

  @Bean(DatabaseBeans.SQLITE_NAMED_JDBC_TEMPLATE_BEAN)
  public NamedParameterJdbcTemplate namedParameterJdbcTemplate(
      @Qualifier(DatabaseBeans.SQLITE_JDBC_TEMPLATE_BEAN) JdbcTemplate jdbcTemplate,
      MeterRegistry metrics) {
    return new NamedParameterJdbcTemplate(SqlQueryMetrics.instrument(jdbcTemplate, metrics));
  }

  @Bean(DatabaseBeans.SQLITE_TX_MANAGER_BEAN)
  public DataSourceTransactionManager transactionManager(
      @Qualifier(DatabaseBeans.SQLITE_DATASOURCE_BEAN) DataSource dataSource) {
    return new DataSourceTransactionManager(dataSource);
  }

  @Bean(DatabaseBeans.SQLITE_TX_TEMPLATE_BEAN)
  public TransactionTemplate transactions(
      @Qualifier(DatabaseBeans.SQLITE_TX_MANAGER_BEAN)
          PlatformTransactionManager transactionManager) {
    return new TransactionTemplate(transactionManager);
  }

  @Bean(DatabaseBeans.SQLITE_LIQUIBASE_BEAN)
  public SpringLiquibase liquibase(
      @Qualifier(DatabaseBeans.SQLITE_DATASOURCE_BEAN) DataSource dataSource,
      @Value("${spring.liquibase.change-log}") String changeLog) {
    SpringLiquibase liquibase = new SpringLiquibase();
    liquibase.setDataSource(dataSource);
    liquibase.setChangeLog(changeLog);
    return liquibase;
  }
}
