package com.portabilidade.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.portabilidade.backend.domain.Authorization;
import com.portabilidade.backend.integration.centralconvenios.CentralConveniosStub;
import com.portabilidade.backend.repository.AuthorizationRepository;
import com.portabilidade.backend.repository.InstallmentLockRepository;
import com.portabilidade.backend.repository.PortabilityOperationRepository;

// Runs the status job by hand (scheduling is disabled in tests) and observes its effects through the API.
@SpringBootTest
@AutoConfigureMockMvc
class PortabilityStatusServiceTest {

	private static final String BEN001_AGENDA = """
			[
			  {"parcelaId":"P001","convenioId":"CONV01","valor":150.00,"dataDesconto":"2026-01-10","status":"ABERTA"},
			  {"parcelaId":"P002","convenioId":"CONV01","valor":150.00,"dataDesconto":"2026-02-10","status":"ABERTA"}
			]""";
	private static final String MARGIN_REJECTION = "Beneficiário atingiu o limite de margem consignável";

	@Autowired
	private PortabilityStatusService portabilityStatusService;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AuthorizationRepository authorizationRepository;

	@Autowired
	private PortabilityOperationRepository operationRepository;

	@Autowired
	private InstallmentLockRepository installmentLockRepository;

	@DynamicPropertySource
	static void centralProperties(DynamicPropertyRegistry registry) {
		CentralConveniosStub.registerProperties(registry);
	}

	@BeforeEach
	void arrangeAuthorizedBeneficiaryWithSchedule() {
		cleanDatabase();
		CentralConveniosStub.reset();
		authorizationRepository.save(new Authorization("BEN001", "central-BEN001"));
		CentralConveniosStub.respondTo("GET", "/beneficiarios/BEN001/agenda", 200, BEN001_AGENDA);
	}

	// Other test classes share this Spring context and database: leave no locks behind.
	@AfterEach
	void cleanDatabase() {
		installmentLockRepository.deleteAll();
		operationRepository.deleteAll();
		authorizationRepository.deleteAll();
	}

	@Test
	void waitsForCentralRetryAfterBeforeAskingAgain() throws Exception {
		var id = submittedOperation("P001", "PROTO-1");
		CentralConveniosStub.respondToWithRetryAfter("GET", "/operacoes-portabilidade/PROTO-1", 204, "", 60);

		portabilityStatusService.refreshPendingOperations();
		portabilityStatusService.refreshPendingOperations();

		assertThat(statusChecks("PROTO-1")).isEqualTo(1);
		operation(id).andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(header().string("Retry-After", matchesPattern("59|60")));
	}

	@Test
	void usesDefaultWaitWhenCentralSendsNoRetryAfter() throws Exception {
		var id = submittedOperation("P001", "PROTO-1");
		CentralConveniosStub.respondTo("GET", "/operacoes-portabilidade/PROTO-1", 204, "");

		portabilityStatusService.refreshPendingOperations();
		portabilityStatusService.refreshPendingOperations();

		assertThat(statusChecks("PROTO-1")).isEqualTo(1);
		operation(id).andExpect(header().string("Retry-After", matchesPattern("1|2")));
	}

	@Test
	void confirmsOperationAndKeepsItsInstallmentsTaken() throws Exception {
		var id = submittedOperation("P001", "PROTO-1");
		CentralConveniosStub.respondTo("GET", "/operacoes-portabilidade/PROTO-1", 200,
				"{\"status\":\"CONFIRMADA\",\"motivoRejeicao\":null}");

		portabilityStatusService.refreshPendingOperations();

		operation(id).andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(header().doesNotExist("Retry-After"));
		schedule().andExpect(jsonPath("$.installments[0].eligible").value(false));
	}

	@Test
	void rejectsOperationWithCentralReasonAndReleasesItsInstallments() throws Exception {
		var id = submittedOperation("P001", "PROTO-1");
		CentralConveniosStub.respondTo("GET", "/operacoes-portabilidade/PROTO-1", 200,
				"{\"status\":\"REJEITADA\",\"motivoRejeicao\":\"" + MARGIN_REJECTION + "\"}");

		portabilityStatusService.refreshPendingOperations();

		operation(id).andExpect(jsonPath("$.status").value("REJECTED"))
				.andExpect(jsonPath("$.rejectionReason").value(MARGIN_REJECTION));
		schedule().andExpect(jsonPath("$.installments[0].eligible").value(true));
	}

	@Test
	void failsOperationUnknownToCentralAndReleasesItsInstallments() throws Exception {
		var id = submittedOperation("P001", "PROTO-1");
		CentralConveniosStub.respondTo("GET", "/operacoes-portabilidade/PROTO-1", 404,
				"{\"erro\":\"Protocolo não encontrado\"}");

		portabilityStatusService.refreshPendingOperations();

		operation(id).andExpect(jsonPath("$.status").value("FAILED"));
		schedule().andExpect(jsonPath("$.installments[0].eligible").value(true));
	}

	@Test
	void keepsOperationPendingWhenCentralIsDownWithoutBlockingOthers() throws Exception {
		var unreachable = submittedOperation("P001", "PROTO-1");
		var confirmed = submittedOperation("P002", "PROTO-2");
		CentralConveniosStub.respondTo("GET", "/operacoes-portabilidade/PROTO-1", 503,
				"{\"erro\":\"Central indisponível no momento (simulado). Tente novamente.\"}");
		CentralConveniosStub.respondTo("GET", "/operacoes-portabilidade/PROTO-2", 200,
				"{\"status\":\"CONFIRMADA\",\"motivoRejeicao\":null}");

		portabilityStatusService.refreshPendingOperations();

		operation(unreachable).andExpect(jsonPath("$.status").value("PENDING"));
		operation(confirmed).andExpect(jsonPath("$.status").value("CONFIRMED"));
	}

	@Test
	void waitsForRetryAfterOfUnavailableCentralBeforeAskingAgain() throws Exception {
		var id = submittedOperation("P001", "PROTO-1");
		CentralConveniosStub.respondToWithRetryAfter("GET", "/operacoes-portabilidade/PROTO-1", 503,
				"{\"erro\":\"Central indisponível no momento (simulado). Tente novamente.\"}", 30);

		portabilityStatusService.refreshPendingOperations();
		portabilityStatusService.refreshPendingOperations();

		assertThat(statusChecks("PROTO-1")).isEqualTo(1);
		operation(id).andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(header().string("Retry-After", matchesPattern("29|30")));
	}

	private String submittedOperation(String installmentId, String protocol) throws Exception {
		CentralConveniosStub.respondTo("POST", "/operacoes-portabilidade", 202, "{\"protocolo\":\"" + protocol + "\"}");
		var result = mockMvc.perform(post("/api/portability-operations")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"beneficiaryId\":\"BEN001\",\"installmentIds\":[\"" + installmentId + "\"],"
								+ "\"destinationAccount\":{\"bankCode\":\"001\",\"branch\":\"1234\","
								+ "\"accountNumber\":\"123456-7\"}}"))
				.andExpect(status().isAccepted())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
	}

	private static long statusChecks(String protocol) {
		return CentralConveniosStub.requests().stream()
				.filter(request -> request.path().equals("/operacoes-portabilidade/" + protocol))
				.count();
	}

	private ResultActions operation(String id) throws Exception {
		return mockMvc.perform(get("/api/portability-operations/{id}", id)).andExpect(status().isOk());
	}

	private ResultActions schedule() throws Exception {
		return mockMvc.perform(get("/api/beneficiaries/BEN001/schedule")).andExpect(status().isOk());
	}

}
