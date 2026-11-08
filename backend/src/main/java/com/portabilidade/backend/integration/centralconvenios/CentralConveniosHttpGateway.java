package com.portabilidade.backend.integration.centralconvenios;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.portabilidade.backend.config.CentralConveniosProperties;
import com.portabilidade.backend.exception.CentralConveniosBadResponseException;
import com.portabilidade.backend.exception.CentralConveniosUnavailableException;

@Component
class CentralConveniosHttpGateway implements CentralConveniosGateway {

	private static final Logger log = LoggerFactory.getLogger(CentralConveniosHttpGateway.class);
	private static final String UNAVAILABLE_MESSAGE = "Central de Convênios is unavailable. Try again later.";
	private static final String AUTHORIZATIONS_PATH = "/autorizacoes";
	private static final String SCHEDULE_PATH = "/beneficiarios/{id}/agenda";
	private static final String PORTABILITY_PATH = "/operacoes-portabilidade";
	private static final String PORTABILITY_STATUS_PATH = "/operacoes-portabilidade/{protocol}";
	private static final ParameterizedTypeReference<List<CentralInstallment>> INSTALLMENT_LIST_TYPE =
			new ParameterizedTypeReference<>() {
			};

	private final RestClient restClient;
	private final Duration defaultRetryAfter;

	CentralConveniosHttpGateway(RestClient.Builder builder, CentralConveniosProperties properties) {
		var requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.connectTimeout());
		requestFactory.setReadTimeout(properties.readTimeout());
		this.restClient = builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
		this.defaultRetryAfter = properties.defaultRetryAfter();
	}

	@Override
	public String createAuthorization(String beneficiaryId) {
		var operation = "POST " + AUTHORIZATIONS_PATH;
		var response = exchange(operation, () -> restClient.post()
				.uri(AUTHORIZATIONS_PATH)
				.body(new CentralAuthorizationRequest(beneficiaryId))
				.retrieve()
				.body(CentralAuthorizationResponse.class));
		if (response.authorizationId() == null) {
			throw badResponse(operation, "missing autorizacaoId");
		}
		return response.authorizationId();
	}

	@Override
	public List<CentralInstallment> getSchedule(String beneficiaryId) {
		var operation = "GET " + SCHEDULE_PATH;
		var installments = exchange(operation, () -> restClient.get()
				.uri(SCHEDULE_PATH, beneficiaryId)
				.retrieve()
				.body(INSTALLMENT_LIST_TYPE));
		if (installments.stream().anyMatch(installment -> installment == null || !installment.isComplete())) {
			throw badResponse(operation, "incomplete installment");
		}
		return installments;
	}

	@Override
	public String submitPortability(CentralPortabilityRequest request) {
		var operation = "POST " + PORTABILITY_PATH;
		var response = exchange(operation, () -> restClient.post()
				.uri(PORTABILITY_PATH)
				.body(request)
				.retrieve()
				.body(CentralPortabilityResponse.class));
		if (response.protocol() == null) {
			throw badResponse(operation, "missing protocolo");
		}
		return response.protocol();
	}

	@Override
	public CentralPortabilityResult getPortabilityStatus(String protocol) {
		var operation = "GET " + PORTABILITY_STATUS_PATH;
		var response = exchange(operation, () -> {
			try {
				return restClient.get()
						.uri(PORTABILITY_STATUS_PATH, protocol)
						.retrieve()
						.toEntity(CentralPortabilityStatusResponse.class);
			} catch (HttpClientErrorException.NotFound e) {
				return ResponseEntity.notFound().<CentralPortabilityStatusResponse>build();
			}
		});
		return toResult(operation, response);
	}

	private CentralPortabilityResult toResult(String operation,
			ResponseEntity<CentralPortabilityStatusResponse> response) {
		if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
			return new CentralPortabilityResult.NotFound();
		}
		if (response.getStatusCode().isSameCodeAs(HttpStatus.NO_CONTENT)) {
			return new CentralPortabilityResult.Pending(retryAfterOf(response.getHeaders()));
		}
		var body = response.getBody();
		if (body == null || body.status() == null) {
			throw badResponse(operation, "missing status");
		}
		return switch (body.status()) {
			case CONFIRMADA -> new CentralPortabilityResult.Confirmed();
			case REJEITADA -> new CentralPortabilityResult.Rejected(body.rejectionReason());
		};
	}

	// The only place where Central HTTP outcomes become exceptions; a missing body counts as a bad response.
	private <T> T exchange(String operation, Supplier<T> call) {
		T body;
		try {
			body = call.get();
		} catch (HttpServerErrorException e) {
			log.warn("Central de Convênios answered {} on {}", e.getStatusCode(), operation);
			throw new CentralConveniosUnavailableException(errorMessageOf(e), retryAfterOf(e.getResponseHeaders()));
		} catch (RestClientException e) {
			if (isTransportFailure(e)) {
				log.warn("Central de Convênios unreachable on {}: {}", operation,
						e.getMostSpecificCause().getClass().getSimpleName());
				throw new CentralConveniosUnavailableException(UNAVAILABLE_MESSAGE, defaultRetryAfter);
			}
			throw badResponse(operation, describe(e));
		}
		if (body == null) {
			throw badResponse(operation, "empty body");
		}
		return body;
	}

	// Never logs the exception message: it may carry the Central's response body (financial data).
	private static String describe(RestClientException e) {
		return e instanceof RestClientResponseException response
				? "status " + response.getStatusCode()
				: e.getMostSpecificCause().getClass().getSimpleName();
	}

	private CentralConveniosBadResponseException badResponse(String operation, String reason) {
		log.warn("Central de Convênios bad response on {}: {}", operation, reason);
		return new CentralConveniosBadResponseException(defaultRetryAfter);
	}

	// ponytail: only the delay-seconds form of Retry-After; an HTTP-date falls back to the default (parse it with
	// DateTimeFormatter.RFC_1123_DATE_TIME if the Central starts sending dates).
	private Duration retryAfterOf(HttpHeaders headers) {
		var value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
		if (value == null || !value.strip().matches("\\d{1,9}")) {
			return defaultRetryAfter;
		}
		return Duration.ofSeconds(Long.parseLong(value.strip()));
	}

	// Timeouts and dropped connections may surface while the body is read, wrapped as a generic RestClientException.
	private static boolean isTransportFailure(RestClientException e) {
		return e instanceof ResourceAccessException || e.getMostSpecificCause() instanceof IOException;
	}

	private static String errorMessageOf(HttpServerErrorException e) {
		try {
			var body = e.getResponseBodyAs(CentralErrorResponse.class);
			return body != null && body.error() != null ? body.error() : UNAVAILABLE_MESSAGE;
		} catch (RuntimeException unparseable) {
			return UNAVAILABLE_MESSAGE;
		}
	}

}
