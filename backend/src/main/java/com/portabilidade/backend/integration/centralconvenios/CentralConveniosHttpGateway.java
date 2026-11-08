package com.portabilidade.backend.integration.centralconvenios;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
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
	private static final ParameterizedTypeReference<List<CentralInstallment>> INSTALLMENT_LIST_TYPE =
			new ParameterizedTypeReference<>() {
			};

	private final RestClient restClient;

	CentralConveniosHttpGateway(RestClient.Builder builder, CentralConveniosProperties properties) {
		var requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.connectTimeout());
		requestFactory.setReadTimeout(properties.readTimeout());
		this.restClient = builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
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

	// The only place where Central HTTP outcomes become exceptions; a missing body counts as a bad response.
	private <T> T exchange(String operation, Supplier<T> call) {
		T body;
		try {
			body = call.get();
		} catch (HttpServerErrorException e) {
			log.warn("Central de Convênios answered {} on {}", e.getStatusCode(), operation);
			throw new CentralConveniosUnavailableException(errorMessageOf(e));
		} catch (RestClientException e) {
			if (isTransportFailure(e)) {
				log.warn("Central de Convênios unreachable on {}: {}", operation,
						e.getMostSpecificCause().getClass().getSimpleName());
				throw new CentralConveniosUnavailableException(UNAVAILABLE_MESSAGE);
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

	private static CentralConveniosBadResponseException badResponse(String operation, String reason) {
		log.warn("Central de Convênios bad response on {}: {}", operation, reason);
		return new CentralConveniosBadResponseException();
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
