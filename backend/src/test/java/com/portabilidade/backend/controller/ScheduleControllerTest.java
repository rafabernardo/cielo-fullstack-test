package com.portabilidade.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.portabilidade.backend.domain.Authorization;
import com.portabilidade.backend.integration.centralconvenios.CentralConveniosStub;
import com.portabilidade.backend.repository.AuthorizationRepository;

// Exercises GET /api/beneficiaries/{id}/schedule end to end; only the Central de Convênios is faked, over real HTTP.
@SpringBootTest
@AutoConfigureMockMvc
class ScheduleControllerTest {

	private static final String BEN001_AGENDA = """
			[
			  {"parcelaId":"P001","convenioId":"CONV01","valor":150.00,"dataDesconto":"2026-01-10","status":"ABERTA"},
			  {"parcelaId":"P002","convenioId":"CONV01","valor":150.10,"dataDesconto":"2026-02-10","status":"ABERTA"}
			]""";

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
	void returnsScheduleOfAuthorizedBeneficiary() throws Exception {
		authorize("BEN001");
		CentralConveniosStub.respond(200, BEN001_AGENDA);

		var result = mockMvc.perform(get("/api/beneficiaries/BEN001/schedule"));

		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.beneficiaryId").value("BEN001"))
				.andExpect(jsonPath("$.installments.length()").value(2))
				.andExpect(jsonPath("$.installments[0].installmentId").value("P001"))
				.andExpect(jsonPath("$.installments[0].agreementId").value("CONV01"))
				.andExpect(jsonPath("$.installments[0].deductionDate").value("2026-01-10"))
				.andExpect(jsonPath("$.installments[0].status").value("OPEN"))
				.andExpect(content().string(containsString("\"amount\":150.00")))
				.andExpect(content().string(containsString("\"amount\":150.10")));
		assertThat(CentralConveniosStub.lastRequest().path()).isEqualTo("/beneficiarios/BEN001/agenda");
	}

	@Test
	void returnsEmptyScheduleWhenCentralHasNoInstallments() throws Exception {
		authorize("BEN003");
		CentralConveniosStub.respond(200, "[]");

		var result = mockMvc.perform(get("/api/beneficiaries/BEN003/schedule"));

		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.beneficiaryId").value("BEN003"))
				.andExpect(jsonPath("$.installments").isEmpty());
	}

	@Test
	void requiresAuthorizationBeforeCallingCentral() throws Exception {
		var result = mockMvc.perform(get("/api/beneficiaries/BEN001/schedule"));

		result.andExpect(status().isUnprocessableContent())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("AUTHORIZATION_REQUIRED"));
		assertThat(CentralConveniosStub.lastRequest()).isNull();
	}

	@Test
	void returnsUnavailableWithCentralMessageWhenCentralAnswers503() throws Exception {
		authorize("BEN001");
		CentralConveniosStub.respond(503,
				"{\"erro\":\"Central indisponível no momento (simulado). Tente novamente.\"}");

		var result = mockMvc.perform(get("/api/beneficiaries/BEN001/schedule"));

		result.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("CENTRAL_CONVENIOS_UNAVAILABLE"))
				.andExpect(jsonPath("$.detail")
						.value("Central indisponível no momento (simulado). Tente novamente."));
	}

	@Test
	void returnsUnavailableWhenCentralDropsTheConnection() throws Exception {
		authorize("BEN001");
		CentralConveniosStub.dropConnection();

		var result = mockMvc.perform(get("/api/beneficiaries/BEN001/schedule"));

		result.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("CENTRAL_CONVENIOS_UNAVAILABLE"));
	}

	@Test
	void returnsUnavailableWhenCentralTimesOut() throws Exception {
		authorize("BEN001");
		CentralConveniosStub.respondAfter(CentralConveniosStub.READ_TIMEOUT.multipliedBy(3), 200, BEN001_AGENDA);

		var result = mockMvc.perform(get("/api/beneficiaries/BEN001/schedule"));

		result.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("CENTRAL_CONVENIOS_UNAVAILABLE"));
	}

	@Test
	void returnsBadGatewayWhenCentralAnswersUnexpected4xx() throws Exception {
		authorize("BEN001");
		CentralConveniosStub.respond(404, "{\"erro\":\"Rota não encontrada\"}");

		var result = mockMvc.perform(get("/api/beneficiaries/BEN001/schedule"));

		result.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.code").value("CENTRAL_CONVENIOS_BAD_RESPONSE"));
	}

	@Test
	void returnsBadGatewayWhenCentralSendsUnknownInstallmentStatus() throws Exception {
		assertBadGatewayFor(200, installmentWithStatus("\"SUSPENSA\""));
	}

	@Test
	void returnsBadGatewayWhenCentralSendsInstallmentWithoutStatus() throws Exception {
		assertBadGatewayFor(200, installmentWithStatus("null"));
	}

	@Test
	void returnsBadGatewayWhenCentralSendsNullInstallment() throws Exception {
		assertBadGatewayFor(200, "[null]");
	}

	@Test
	void returnsBadGatewayWhenCentralAnswersWithoutBody() throws Exception {
		assertBadGatewayFor(200, "");
	}

	private void assertBadGatewayFor(int centralStatus, String centralBody) throws Exception {
		authorize("BEN001");
		CentralConveniosStub.respond(centralStatus, centralBody);

		var result = mockMvc.perform(get("/api/beneficiaries/BEN001/schedule"));

		result.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.code").value("CENTRAL_CONVENIOS_BAD_RESPONSE"));
	}

	private static String installmentWithStatus(String status) {
		return "[{\"parcelaId\":\"P001\",\"convenioId\":\"CONV01\",\"valor\":150.00,"
				+ "\"dataDesconto\":\"2026-01-10\",\"status\":" + status + "}]";
	}

	private void authorize(String beneficiaryId) {
		authorizationRepository.save(new Authorization(beneficiaryId, "central-" + beneficiaryId));
	}

}
