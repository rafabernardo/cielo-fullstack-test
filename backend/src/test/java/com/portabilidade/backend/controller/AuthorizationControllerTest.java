package com.portabilidade.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import com.portabilidade.backend.domain.Authorization;
import com.portabilidade.backend.integration.centralconvenios.CentralConveniosStub;
import com.portabilidade.backend.integration.centralconvenios.CentralConveniosStub.ReceivedRequest;
import com.portabilidade.backend.repository.AuthorizationRepository;

// Exercises POST /api/authorizations end to end; only the Central de Convênios is faked, over real HTTP.
@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AuthorizationRepository authorizationRepository;

	@DynamicPropertySource
	static void centralProperties(DynamicPropertyRegistry registry) {
		CentralConveniosStub.registerProperties(registry);
	}

	@BeforeEach
	void reset() {
		authorizationRepository.deleteAll();
		CentralConveniosStub.reset();
	}

	@Test
	void registersAuthorizationAtCentralAndStoresIt() throws Exception {
		var centralId = UUID.randomUUID().toString();
		CentralConveniosStub.respond(201, "{\"autorizacaoId\":\"" + centralId + "\"}");

		var result = mockMvc.perform(createAuthorization("{\"beneficiaryId\":\"BEN001\"}"));

		result.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.beneficiaryId").value("BEN001"))
				.andExpect(jsonPath("$.centralAuthorizationId").value(centralId))
				.andExpect(jsonPath("$.createdAt").isNotEmpty());
		assertThat(CentralConveniosStub.lastRequest())
				.isEqualTo(new ReceivedRequest("POST", "/autorizacoes", "{\"beneficiarioId\":\"BEN001\"}"));
		assertThat(authorizationRepository.findAll())
				.singleElement()
				.extracting(Authorization::getBeneficiaryId, Authorization::getCentralAuthorizationId)
				.containsExactly("BEN001", centralId);
	}

	@Test
	void rejectsBlankBeneficiaryIdWithoutCallingCentral() throws Exception {
		var result = mockMvc.perform(createAuthorization("{\"beneficiaryId\":\" \"}"));

		result.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors.beneficiaryId").isNotEmpty());
		assertThat(CentralConveniosStub.lastRequest()).isNull();
		assertThat(authorizationRepository.count()).isZero();
	}

	@Test
	void rejectsMalformedBody() throws Exception {
		var result = mockMvc.perform(createAuthorization("{not json"));

		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		assertThat(authorizationRepository.count()).isZero();
	}

	@Test
	void returnsUnavailableWithCentralMessageWhenCentralAnswers503() throws Exception {
		CentralConveniosStub.respond(503,
				"{\"erro\":\"Central indisponível no momento (simulado). Tente novamente.\"}");

		var result = mockMvc.perform(createAuthorization("{\"beneficiaryId\":\"BEN001\"}"));

		result.andExpect(status().isServiceUnavailable())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("CENTRAL_CONVENIOS_UNAVAILABLE"))
				.andExpect(jsonPath("$.detail").value("Central indisponível no momento (simulado). Tente novamente."))
				.andExpect(jsonPath("$.instance").value("/api/authorizations"));
		assertThat(authorizationRepository.count()).isZero();
	}

	@Test
	void returnsUnavailableWhenCentralDropsTheConnection() throws Exception {
		CentralConveniosStub.dropConnection();

		var result = mockMvc.perform(createAuthorization("{\"beneficiaryId\":\"BEN001\"}"));

		result.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("CENTRAL_CONVENIOS_UNAVAILABLE"));
		assertThat(authorizationRepository.count()).isZero();
	}

	@Test
	void returnsUnavailableWhenCentralTimesOut() throws Exception {
		CentralConveniosStub.respondAfter(CentralConveniosStub.READ_TIMEOUT.multipliedBy(3), 201,
				"{\"autorizacaoId\":\"late\"}");

		var result = mockMvc.perform(createAuthorization("{\"beneficiaryId\":\"BEN001\"}"));

		result.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("CENTRAL_CONVENIOS_UNAVAILABLE"));
		assertThat(authorizationRepository.count()).isZero();
	}

	@Test
	void returnsBadGatewayWhenCentralAnswersUnexpected4xx() throws Exception {
		CentralConveniosStub.respond(404, "{\"erro\":\"Rota não encontrada\"}");

		var result = mockMvc.perform(createAuthorization("{\"beneficiaryId\":\"BEN001\"}"));

		result.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.code").value("CENTRAL_CONVENIOS_BAD_RESPONSE"));
		assertThat(authorizationRepository.count()).isZero();
	}

	@Test
	void returnsBadGatewayWhenCentralAnswersWithoutAuthorizationId() throws Exception {
		CentralConveniosStub.respond(201, "{}");

		var result = mockMvc.perform(createAuthorization("{\"beneficiaryId\":\"BEN001\"}"));

		result.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.code").value("CENTRAL_CONVENIOS_BAD_RESPONSE"));
		assertThat(authorizationRepository.count()).isZero();
	}

	private static RequestBuilder createAuthorization(String body) {
		return post("/api/authorizations").contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
