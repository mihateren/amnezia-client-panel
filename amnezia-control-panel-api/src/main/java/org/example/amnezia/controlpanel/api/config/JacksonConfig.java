package org.example.amnezia.controlpanel.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class JacksonConfig {

    @Bean
    ObjectMapper jacksonObjectMapper() {
        return new ObjectMapper();
    }

}