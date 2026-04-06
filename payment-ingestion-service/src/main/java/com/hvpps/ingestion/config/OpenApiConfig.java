package com.hvpps.ingestion.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentIngestionOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Payment Ingestion API")
                        .version("v1")
                        .description("Sync REST endpoint for ingesting payment requests."));
    }
}
