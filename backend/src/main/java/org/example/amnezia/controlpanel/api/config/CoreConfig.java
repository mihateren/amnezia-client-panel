package org.example.amnezia.controlpanel.api.config;

import java.time.Clock;
import java.util.concurrent.locks.ReentrantLock;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
public class CoreConfig {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  ReentrantLock mutationLock() {
    return new ReentrantLock(true);
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
  }

  @Bean
  JdbcTemplate jdbcTemplate(DataSource ds) {
    var jdbc = new JdbcTemplate(ds);
    var fallback = new org.springframework.jdbc.support.SQLExceptionSubclassTranslator();
    jdbc.setExceptionTranslator(
        (task, sql, exception) ->
            exception.getErrorCode() == 19
                ? new org.springframework.dao.DataIntegrityViolationException(
                    "Database constraint violation")
                : fallback.translate(task, sql, exception));
    return jdbc;
  }

  @Bean
  TransactionTemplate transactions(PlatformTransactionManager tm) {
    return new TransactionTemplate(tm);
  }
}
