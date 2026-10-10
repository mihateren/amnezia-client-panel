package org.example.amnezia.controlpanel.api.config;

import java.time.Clock;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

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
}
