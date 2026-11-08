package com.portabilidade.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

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
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.portabilidade.backend.domain.Authorization;
import com.portabilidade.backend.integration.centralconvenios.CentralConveniosStub;
import com.portabilidade.backend.repository.AuthorizationRepository;
import com.portabilidade.backend.repository.InstallmentLockRepository;
import com.portabilidade.backend.repository.PortabilityOperationRepository;
import com.portabilidade.backend.service.PortabilityStatusService;

// Subscribes to an operation's SSE stream and drives status changes by running the status job by hand.
@SpringBootTest
@AutoConfigureMockMvc
class PortabilityOperationEventsTest {

	private static final String BEN001_AGENDA = """
			[
			  {"parcelaId":"P001","convenioId":"CONV01","valor":150.00,"dataDesconto":"2026-01-10","status":"ABERTA"}
			]""";
	private static final String STATUS_PATH = "/operacoes-portabilidade/PROTO-1";
	private static final String CONFIRMED = "{\"status\":\"CONFIRMADA\",\"motivoRejeicao\":null}";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PortabilityStatusService portabilityStatusService;

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
		CentralConveniosStub.respondTo("POST", "/operacoes-portabilidade", 202, "{\"protocolo\":\"PROTO-1\"}");
	}

	// Other test classes share this Spring context and database: leave no locks behind.
	@AfterEach
	void cleanDatabase() {
		installmentLockRepository.deleteAll();
		operationRepository.deleteAll();
		authorizationRepository.deleteAll();
	}

	@Test
	void sendsCurrentStatusOnSubscribeThenPushesConfirmationAndCompletes() throws Exception {
		var id = createOperation();

		var stream = subscribe(id);

		assertThat(events(stream)).containsExactly("PENDING");

		CentralConveniosStub.respondTo("GET", STATUS_PATH, 200, CONFIRMED);
		portabilityStatusService.refreshPendingOperations();

		assertThat(events(stream)).containsExactly("PENDING", "CONFIRMED");
		assertThat(stream.getAsyncResult(1_000)).isNull();
	}

	@Test
	void pushesNothingWhileCentralHasNotDecided() throws Exception {
		var id = createOperation();
		var stream = subscribe(id);
		CentralConveniosStub.respondTo("GET", STATUS_PATH, 204, "");

		portabilityStatusService.refreshPendingOperations();

		assertThat(events(stream)).containsExactly("PENDING");
	}

	@Test
	void sendsFinalStatusAndCompletesWhenOperationIsAlreadySettled() throws Exception {
		var id = createOperation();
		CentralConveniosStub.respondTo("GET", STATUS_PATH, 200, CONFIRMED);
		portabilityStatusService.refreshPendingOperations();

		var stream = subscribe(id);

		assertThat(events(stream)).containsExactly("CONFIRMED");
		assertThat(stream.getAsyncResult(1_000)).isNull();
	}

	@Test
	void returnsNotFoundBeforeOpeningStreamForUnknownOperation() throws Exception {
		var result = mockMvc.perform(get("/api/portability-operations/{id}/events", UUID.randomUUID())
				.accept(MediaType.TEXT_EVENT_STREAM));

		result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PORTABILITY_OPERATION_NOT_FOUND"));
	}

	private String createOperation() throws Exception {
		var result = mockMvc.perform(post("/api/portability-operations")
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"beneficiaryId\":\"BEN001\",\"installmentIds\":[\"P001\"],\"destinationAccount\":"
								+ "{\"bankCode\":\"001\",\"branch\":\"1234\",\"accountNumber\":\"123456-7\"}}"))
				.andExpect(status().isAccepted())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
	}

	private MvcResult subscribe(String id) throws Exception {
		return mockMvc.perform(get("/api/portability-operations/{id}/events", id).accept(MediaType.TEXT_EVENT_STREAM))
				.andExpect(request().asyncStarted())
				.andReturn();
	}

	// The status of every "status" event received so far, in order.
	private static List<String> events(MvcResult stream) throws Exception {
		var body = stream.getResponse().getContentAsString();
		return Arrays.stream(body.split("\n\n"))
				.filter(event -> event.contains("event:status"))
				.map(event -> event.substring(event.indexOf("data:") + "data:".length()))
				.map(data -> JsonPath.<String>read(data, "$.status"))
				.toList();
	}

}
