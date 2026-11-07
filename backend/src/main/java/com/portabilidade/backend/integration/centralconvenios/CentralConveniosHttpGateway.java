package com.portabilidade.backend.integration.centralconvenios;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.portabilidade.backend.config.CentralConveniosProperties;
import com.portabilidade.backend.exception.CentralConveniosBadResponseException;
import com.portabilidade.backend.exception.CentralConveniosUnavailableException;

@Component
class CentralConveniosHttpGateway implements CentralConveniosGateway {

	private static final Logger log = LoggerFactory.getLogger(CentralConveniosHttpGateway.class);
	private static final String UNAVAILABLE_MESSAGE = "Central de Convênios is unavailable. Try again later.";

	private final RestClient restClient;

	CentralConveniosHttpGateway(RestClient.Builder builder, CentralConveniosProperties properties) {
		var requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.connectTimeout());
		requestFactory.setReadTimeout(properties.readTimeout());
		this.restClient = builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
	}

	@Override
	public String createAuthorization(String beneficiaryId) {
		CentralAuthorizationResponse response;
		try {
			response = restClient.post()
					.uri("/autorizacoes")
					.body(new CentralAuthorizationRequest(beneficiaryId))
					.retrieve()
					.body(CentralAuthorizationResponse.class);
		} catch (HttpServerErrorException e) {
			log.warn("Central de Convênios answered {} on POST /autorizacoes", e.getStatusCode());
			throw new CentralConveniosUnavailableException(errorMessageOf(e));
		} catch (RestClientException e) {
			if (isTransportFailure(e)) {
				log.warn("Central de Convênios unreachable on POST /autorizacoes: {}", e.getMostSpecificCause().toString());
				throw new CentralConveniosUnavailableException(UNAVAILABLE_MESSAGE);
			}
			log.warn("Central de Convênios bad response on POST /autorizacoes: {}", e.getMessage());
			throw new CentralConveniosBadResponseException();
		}
		if (response == null || response.authorizationId() == null) {
			log.warn("Central de Convênios answered POST /autorizacoes without autorizacaoId");
			throw new CentralConveniosBadResponseException();
		}
		return response.authorizationId();
	}

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
