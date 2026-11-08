package com.portabilidade.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

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
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.portabilidade.backend.domain.Authorization;
import com.portabilidade.backend.domain.PortabilityOperationStatus;
import com.portabilidade.backend.integration.centralconvenios.CentralConveniosStub;
import com.portabilidade.backend.integration.centralconvenios.CentralConveniosStub.ReceivedRequest;
import com.portabilidade.backend.repository.AuthorizationRepository;
import com.portabilidade.backend.repository.InstallmentLockRepository;
import com.portabilidade.backend.repository.PortabilityOperationRepository;

// Exercises portability operation creation end to end; only the Central de Convênios is faked, over real HTTP.
@SpringBootTest
@AutoConfigureMockMvc
class PortabilityOperationControllerTest {

	private static final String SCHEDULE_PATH = "/beneficiarios/BEN001/agenda";
	private static final String SUBMIT_PATH = "/operacoes-portabilidade";
	private static final String BEN001_AGENDA = """
			[
			  {"parcelaId":"P001","convenioId":"CONV01","valor":150.00,"dataDesconto":"2026-01-10","status":"ABERTA"},
			  {"parcelaId":"P002","convenioId":"CONV01","valor":150.00,"dataDesconto":"2026-02-10","status":"ABERTA"}
			]""";
	private static final String ACCOUNT = """
			{"bankCode":"001","branch":"1234","accountNumber":"123456-7"}""";
	private static final String UNAVAILABLE =
			"{\"erro\":\"Central indisponível no momento (simulado). Tente novamente.\"}";

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
		CentralConveniosStub.respondTo("GET", SCHEDULE_PATH, 200, BEN001_AGENDA);
		centralAcceptsSubmissions();
	}

	// Other test classes share this Spring context and database: leave no locks behind.
	@AfterEach
	void cleanDatabase() {
		installmentLockRepository.deleteAll();
		operationRepository.deleteAll();
		authorizationRepository.deleteAll();
	}

	@Test
	void createsPendingOperationAndSubmitsItToCentral() throws Exception {
		var key = UUID.randomUUID();

		var result = create(key, requestFor("P001", "P002"));

		result.andExpect(status().isAccepted())
				.andExpect(header().string("Location", matchesPattern(
						"http://localhost/api/portability-operations/[0-9a-f-]{36}")))
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.beneficiaryId").value("BEN001"))
				.andExpect(jsonPath("$.installmentIds").value(contains("P001", "P002")))
				.andExpect(jsonPath("$.destinationAccount.bankCode").value("001"))
				.andExpect(jsonPath("$.destinationAccount.branch").value("1234"))
				.andExpect(jsonPath("$.destinationAccount.accountNumber").value("123456-7"))
				.andExpect(jsonPath("$.status").value("PENDING"));
		assertThat(submissions()).singleElement().extracting(ReceivedRequest::body).isEqualTo(
				"{\"beneficiarioId\":\"BEN001\",\"parcelaIds\":[\"P001\",\"P002\"],"
						+ "\"contaDestino\":{\"banco\":\"001\",\"agencia\":\"1234\",\"conta\":\"123456-7\"}}");
	}

	@Test
	void readsStoredOperationWithRetryAfterWhilePending() throws Exception {
		var id = idOf(create(UUID.randomUUID(), requestFor("P001")).andReturn());
		var requestsBeforeRead = CentralConveniosStub.requests().size();

		var result = mockMvc.perform(get("/api/portability-operations/{id}", id));

		result.andExpect(status().isOk())
				.andExpect(header().string("Retry-After", "2"))
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.status").value("PENDING"));
		assertThat(CentralConveniosStub.requests()).hasSize(requestsBeforeRead);
	}

	@Test
	void returnsNotFoundForUnknownOperation() throws Exception {
		var result = mockMvc.perform(get("/api/portability-operations/{id}", UUID.randomUUID()));

		result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PORTABILITY_OPERATION_NOT_FOUND"));
	}

	@Test
	void requiresIdempotencyKey() throws Exception {
		var result = mockMvc.perform(post("/api/portability-operations")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestFor("P001")));

		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
		assertThat(operationRepository.count()).isZero();
	}

	@Test
	void rejectsIdempotencyKeyThatIsNotUuid() throws Exception {
		var result = mockMvc.perform(post("/api/portability-operations")
				.header("Idempotency-Key", "not-a-uuid")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestFor("P001")));

		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
	}

	@Test
	void rejectsEmptyAndDuplicatedInstallmentsAndMalformedAccount() throws Exception {
		var empty = create(UUID.randomUUID(), "{\"beneficiaryId\":\"BEN001\",\"installmentIds\":[],"
				+ "\"destinationAccount\":" + ACCOUNT + "}");
		var duplicated = create(UUID.randomUUID(), requestFor("P001", "P001"));
		var badAccount = create(UUID.randomUUID(), "{\"beneficiaryId\":\"BEN001\",\"installmentIds\":[\"P001\"],"
				+ "\"destinationAccount\":{\"bankCode\":\"1\",\"branch\":\"12a4\",\"accountNumber\":\"\"}}");

		empty.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors.installmentIds").isNotEmpty());
		duplicated.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.installmentIdsUnique").isNotEmpty());
		badAccount.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors['destinationAccount.bankCode']").isNotEmpty())
				.andExpect(jsonPath("$.errors['destinationAccount.branch']").isNotEmpty())
				.andExpect(jsonPath("$.errors['destinationAccount.accountNumber']").isNotEmpty());
		assertThat(operationRepository.count()).isZero();
	}

	@Test
	void requiresAuthorizationOfBeneficiary() throws Exception {
		authorizationRepository.deleteAll();

		var result = create(UUID.randomUUID(), requestFor("P001"));

		result.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("AUTHORIZATION_REQUIRED"));
		assertThat(operationRepository.count()).isZero();
		assertThat(submissions()).isEmpty();
	}

	@Test
	void rejectsInstallmentsMissingFromSchedule() throws Exception {
		var result = create(UUID.randomUUID(), requestFor("P001", "P999"));

		result.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INSTALLMENT_NOT_ELIGIBLE"))
				.andExpect(jsonPath("$.installmentIds").value(contains("P999")));
		assertThat(operationRepository.count()).isZero();
	}

	@Test
	void storesNothingWhenCentralIsUnavailableWhileValidatingSchedule() throws Exception {
		CentralConveniosStub.respondTo("GET", SCHEDULE_PATH, 503, UNAVAILABLE);

		var result = create(UUID.randomUUID(), requestFor("P001"));

		result.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("CENTRAL_CONVENIOS_UNAVAILABLE"));
		assertThat(operationRepository.count()).isZero();
		assertThat(submissions()).isEmpty();
	}

	@Test
	void failsOperationAndReleasesInstallmentsWhenCentralRefusesSubmission() throws Exception {
		CentralConveniosStub.respondTo("POST", SUBMIT_PATH, 503, UNAVAILABLE);

		var result = create(UUID.randomUUID(), requestFor("P001"));

		result.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("CENTRAL_CONVENIOS_UNAVAILABLE"))
				.andExpect(jsonPath("$.detail").value("Central indisponível no momento (simulado). Tente novamente."));
		assertThat(operationRepository.findAll()).singleElement()
				.extracting(operation -> operation.getStatus()).isEqualTo(PortabilityOperationStatus.FAILED);
		assertThat(installmentLockRepository.count()).isZero();
		schedule().andExpect(jsonPath("$.installments[0].eligible").value(true));
	}

	@Test
	void returnsExistingOperationWhenIdempotencyKeyIsRepeated() throws Exception {
		var key = UUID.randomUUID();
		var firstId = idOf(create(key, requestFor("P001", "P002")).andReturn());

		var replay = create(key, requestFor("P002", "P001"));

		replay.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(firstId));
		assertThat(operationRepository.count()).isOne();
		assertThat(submissions()).hasSize(1);
	}

	@Test
	void rejectsIdempotencyKeyReusedWithDifferentPayload() throws Exception {
		var key = UUID.randomUUID();
		create(key, requestFor("P001"));

		var result = create(key, requestFor("P002"));

		result.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
		assertThat(operationRepository.count()).isOne();
	}

	@Test
	void resubmitsFailedOperationWhenRetriedWithSameKey() throws Exception {
		var key = UUID.randomUUID();
		CentralConveniosStub.respondTo("POST", SUBMIT_PATH, 503, UNAVAILABLE);
		create(key, requestFor("P001")).andExpect(status().isServiceUnavailable());
		var failedId = operationRepository.findAll().getFirst().getId().toString();
		centralAcceptsSubmissions();

		var retry = create(key, requestFor("P001"));

		retry.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.id").value(failedId))
				.andExpect(jsonPath("$.status").value("PENDING"));
		assertThat(operationRepository.count()).isOne();
		assertThat(submissions()).hasSize(2);
		schedule().andExpect(jsonPath("$.installments[0].eligible").value(false));
	}

	@Test
	void rejectsInstallmentAlreadyInActiveOperation() throws Exception {
		create(UUID.randomUUID(), requestFor("P001"));

		var result = create(UUID.randomUUID(), requestFor("P001", "P002"));

		result.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INSTALLMENT_IN_OPERATION"))
				.andExpect(jsonPath("$.installmentIds").value(contains("P001")));
		assertThat(operationRepository.count()).isOne();
		schedule().andExpect(jsonPath("$.installments[0].eligible").value(false))
				.andExpect(jsonPath("$.installments[1].eligible").value(true));
	}

	@Test
	void rejectsRetryWhenInstallmentWasTakenMeanwhile() throws Exception {
		var key = UUID.randomUUID();
		CentralConveniosStub.respondTo("POST", SUBMIT_PATH, 503, UNAVAILABLE);
		create(key, requestFor("P001"));
		centralAcceptsSubmissions();
		create(UUID.randomUUID(), requestFor("P001"));

		var retry = create(key, requestFor("P001"));

		retry.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INSTALLMENT_IN_OPERATION"));
	}

	@Test
	void createsOneOperationForConcurrentRequestsWithSameKey() throws Exception {
		CentralConveniosStub.respondToAfter("GET", SCHEDULE_PATH, Duration.ofMillis(200), 200, BEN001_AGENDA);
		var key = UUID.randomUUID();

		var results = inParallel(() -> create(key, requestFor("P001")).andReturn(),
				() -> create(key, requestFor("P001")).andReturn());

		assertThat(results).extracting(result -> result.getResponse().getStatus())
				.containsExactlyInAnyOrder(202, 200);
		assertThat(results).extracting(PortabilityOperationControllerTest::idOf).containsOnly(idOf(results.get(0)));
		assertThat(operationRepository.count()).isOne();
		assertThat(submissions()).hasSize(1);
	}

	@Test
	void letsOnlyOneOfConcurrentOperationsTakeTheSameInstallment() throws Exception {
		CentralConveniosStub.respondToAfter("GET", SCHEDULE_PATH, Duration.ofMillis(200), 200, BEN001_AGENDA);

		var results = inParallel(() -> create(UUID.randomUUID(), requestFor("P001")).andReturn(),
				() -> create(UUID.randomUUID(), requestFor("P001")).andReturn());

		assertThat(results).extracting(result -> result.getResponse().getStatus())
				.containsExactlyInAnyOrder(202, 409);
		assertThat(operationRepository.count()).isOne();
		assertThat(installmentLockRepository.count()).isOne();
	}

	private ResultActions create(UUID key, String body) throws Exception {
		return mockMvc.perform(post("/api/portability-operations")
				.header("Idempotency-Key", key.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private ResultActions schedule() throws Exception {
		CentralConveniosStub.respondTo("GET", SCHEDULE_PATH, 200, BEN001_AGENDA);
		return mockMvc.perform(get("/api/beneficiaries/BEN001/schedule"));
	}

	private static String requestFor(String... installmentIds) {
		var ids = String.join(",", List.of(installmentIds).stream().map(id -> "\"" + id + "\"").toList());
		return "{\"beneficiaryId\":\"BEN001\",\"installmentIds\":[" + ids + "],\"destinationAccount\":" + ACCOUNT + "}";
	}

	private static void centralAcceptsSubmissions() {
		CentralConveniosStub.respondTo("POST", SUBMIT_PATH, 202, "{\"protocolo\":\"" + UUID.randomUUID() + "\"}");
	}

	private static List<ReceivedRequest> submissions() {
		return CentralConveniosStub.requests().stream().filter(request -> request.path().equals(SUBMIT_PATH)).toList();
	}

	private static String idOf(MvcResult result) {
		try {
			return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	@SafeVarargs
	private static List<MvcResult> inParallel(Callable<MvcResult>... calls) throws Exception {
		try (var executor = Executors.newFixedThreadPool(calls.length)) {
			var futures = executor.invokeAll(List.of(calls));
			var results = new ArrayList<MvcResult>();
			for (var future : futures) {
				results.add(future.get());
			}
			return results;
		}
	}

}
