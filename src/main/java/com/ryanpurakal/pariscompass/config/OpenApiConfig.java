package com.ryanpurakal.pariscompass.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI document at /v3/api-docs, Swagger UI at /swagger-ui.html. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI parisCompassOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Paris Compass API")
                .version("v1")
                .description("""
                        Country-level CO2, electricity and temperature data from Our World in Data and Copernicus ERA5, \
                        a deterministic Paris alignment score, and schema-validated five-year CO2 projections.

                        Every error is RFC 9457 `application/problem+json` with a stable `code` field.""")
                .license(new License().name("Data: CC BY 4.0 (Our World in Data)")
                        .url("https://creativecommons.org/licenses/by/4.0/")));
    }
}
