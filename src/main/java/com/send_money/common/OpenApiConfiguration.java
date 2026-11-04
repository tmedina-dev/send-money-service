package com.send_money.common;

import java.util.List;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI sendMoneyOpenApi() {
        return new OpenAPI().info(new Info().title("Send Money API").version("v1")
                .description("Wallet balances, eligible recipients, and transfers. All amounts are PHP. "
                        + "Authentication is handled by an upstream gateway, which supplies X-User-Id."));
    }

    @Bean
    OpenApiCustomizer commonApiDocumentation() {
        return api -> {
            if (api.getComponents() == null) {
                api.setComponents(new Components());
            }
            api.getComponents().addSchemas("ApiError", new ObjectSchema()
                    .addProperty("detail", new StringSchema().example("Invalid request."))
                    .required(List.of("detail")));
            api.getPaths().forEach((path, item) -> {
            if (!path.startsWith("/api/v1/")) {
                return;
            }
            item.readOperations().forEach(operation -> {
                // Replace the inferred optional header: validation is performed by the service.
                if (operation.getParameters() != null) {
                    operation.getParameters().removeIf(parameter -> "header".equals(parameter.getIn())
                            && "X-User-Id".equalsIgnoreCase(parameter.getName()));
                }
                operation.addParametersItem(new Parameter().name("X-User-Id").in("header").required(true)
                        .description("Current user's positive numeric ID; must identify an existing wallet.")
                        .schema(new StringSchema().pattern("^[1-9][0-9]*$")).example("1"));
                operation.addParametersItem(new Parameter().name("X-Request-Id").in("header").required(false)
                        .description("Optional correlation ID. Invalid or missing IDs are replaced with a UUID.")
                        .schema(new StringSchema().pattern("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$"))
                        .example("swagger-request-001"));
                operation.getResponses().addApiResponse("400", error("Invalid or missing user ID", "Invalid request."));
                operation.getResponses().addApiResponse("404", error("Current user's wallet not found", "Requested resource not found."));
                operation.getResponses().addApiResponse("502", error("External user directory failed", "Unable to process your request."));
                if (path.equals("/api/v1/transfers")) {
                    ApiResponse conflict = error("Insufficient funds, balance capacity, or conflicting retry key", "Unable to process your request.");
                    conflict.getContent().get("application/json").addExamples("insufficientBalance",
                            new Example().value(java.util.Map.of("detail", com.send_money.transfer.InsufficientBalanceException.MESSAGE)));
                    operation.getResponses().addApiResponse("409", conflict);
                    operation.getResponses().addApiResponse("500", error("Transfer could not be persisted; changes rolled back", "Unable to process your request."));
                }
                operation.getResponses().values().forEach(response -> response.addHeaderObject("X-Request-Id",
                        new Header().description("Correlation ID for application logs").schema(new StringSchema())));
            });
            });
        };
    }

    private ApiResponse error(String description, String detail) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/json",
                new MediaType().schema(new ObjectSchema().$ref("#/components/schemas/ApiError"))
                        .addExamples("error", new Example().value(java.util.Map.of("detail", detail)))));
    }
}
