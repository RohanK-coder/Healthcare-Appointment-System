package com.example.healthcare.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI careSlotOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CareSlot Healthcare Appointment API")
                        .version("1.0")
                        .description("""
                                Demo system with synthetic data. Not a clinical product, not HIPAA compliant;
                                do not submit real personal or health information.

                                Authenticate with POST /api/auth/login, then click Authorize and paste the token.
                                Routes under /api/patient, /api/doctor and /api/admin require that role."""))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
