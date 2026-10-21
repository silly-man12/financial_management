package com.example.financial_management.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;

@Configuration
public class SwaggerConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Financial Management API")
                        .version("v1")
                        .description("API for Financial Management Application"))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }

    /**
     * Tùy chỉnh rule áp dụng security và gán operationId duy nhất để tránh xung đột trên Swagger UI
     */
    @Bean
    public GlobalOpenApiCustomizer globalOpenApiCustomizer() {
        return openAPi -> {
            openAPi.getPaths().forEach((path, pathItem) -> {
                pathItem.readOperationsMap().forEach((httpMethod, operation) -> {
                    // Đảm bảo operationId luôn là duy nhất trên toàn bộ Swagger UI để tránh lỗi xung đột sự kiện click Execute
                    String cleanPath = path.replaceAll("[^a-zA-Z0-9_]", "_");
                    operation.setOperationId(httpMethod.name().toLowerCase() + cleanPath);

                    boolean isPublicEndpoint = isPublicEndpoint(path);
                    if (!isPublicEndpoint) {
                        operation.addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
                        addAuthenticationDescription(operation, true);
                    } else {
                        addAuthenticationDescription(operation, false);
                    }
                });
            });
        };
    }

    private void addAuthenticationDescription(Operation operation, boolean requireAuth) {
        String currentDescription = operation.getDescription() != null ? operation.getDescription() : "";
        String authDescription;

        if (requireAuth) {
            authDescription = "\n\n Authentication required: Use the 'Authorize' button to enter your JWT token.\n";
        } else {
            authDescription = "\n\n No authentication required.";
        }

        operation.setDescription(currentDescription + authDescription);
    }

    private boolean isPublicEndpoint(String path) {
        if (path == null) {
            return false;
        }

        for (String publicPath : SecurityConfig.PUBLIC_ENDPOINTS) {
            if (antPathMatcher.match(path, publicPath)) {
                return true;
            }
        }
        return false;
    }
}
