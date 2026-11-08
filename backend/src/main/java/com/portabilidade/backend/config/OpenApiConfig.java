package com.portabilidade.backend.config;

import java.util.List;
import java.util.Map;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;

/**
 * Every error is an RFC 7807 problem (see ApiExceptionHandler): controllers only declare which status codes they
 * return, and this gives all 4xx/5xx responses the same problem body.
 */
@Configuration
public class OpenApiConfig {

	private static final String PROBLEM_SCHEMA = "Problem";

	@Bean
	OpenApiCustomizer problemResponses() {
		var problem = new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
				new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA)));
		return openApi -> {
			openApi.schema(PROBLEM_SCHEMA, problemSchema());
			openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> operation
					.getResponses()
					.forEach((status, response) -> {
						if (status.startsWith("4") || status.startsWith("5")) {
							response.setContent(problem);
						}
					})));
		};
	}

	private static Schema<?> problemSchema() {
		return new ObjectSchema()
				.addProperty("type", new StringSchema().example("about:blank"))
				.addProperty("title", new StringSchema().example("Unprocessable Content"))
				.addProperty("status", new IntegerSchema().example(422))
				.addProperty("detail", new StringSchema())
				.addProperty("instance", new StringSchema().example("/api/portability-operations"))
				.addProperty("code", new StringSchema().description("Machine-readable error code; see each response")
						.example("INSTALLMENT_NOT_ELIGIBLE"))
				.addProperty("errors", new MapSchema().additionalProperties(new StringSchema())
						.description("VALIDATION_ERROR only: invalid field -> message")
						.example(Map.of("beneficiaryId", "must not be blank")))
				.addProperty("installmentIds", new ArraySchema().items(new StringSchema())
						.description("INSTALLMENT_NOT_ELIGIBLE and INSTALLMENT_IN_*_OPERATION only"))
				.required(List.of("type", "title", "status", "code"));
	}

}
