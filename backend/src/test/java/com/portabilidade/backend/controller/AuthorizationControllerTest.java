package com.portabilidade.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
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
import com.portabilidade.backend.repository.AuthorizationRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

// Exercises POST /api/authorizations end to end; only the Central de Convênios is faked, over real HTTP.
@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationControllerTest {

	private static final AtomicReference<HttpHandler> CENTRAL_BEHAVIOR = new AtomicReference<>();
	private static final AtomicReference<String> CENTRAL_RECEIVED_BODY = new AtomicReference<>();
	private static final HttpServer CENTRAL = startCentral();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AuthorizationRepository authorizationRepository;

	@DynamicPropertySource
	static void centralProperties(DynamicPropertyRegistry registry) {
		registry.add("central-convenios.base-url", () -> "http://localhost:" + CENTRAL.getAddress().getPort());
		registry.add("central-convenios.read-timeout", () -> "500ms");
	}

	@AfterAll
	static void stopCentral() {
		CENTRAL.stop(0);
	}

	@BeforeEach
	void reset() {
		authorizationRepository.deleteAll();
		CENTRAL_RECEIVED_BODY.set(null);
	}

	@Test
	void registersAuthorizationAtCentralAndStoresIt() throws Exception {
		var centralId = UUID.randomUUID().toString();
		centralResponds(201, "{\"autorizacaoId\":\"" + centralId + "\"}");

		var result = mockMvc.perform(createAuthorization("{\"beneficiaryId\":\"BEN001\"}"));

		result.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.beneficiaryId").value("BEN001"))
				.andExpect(jsonPath("$.centralAuthorizationId").value(centralId))
				.andExpect(jsonPath("$.createdAt").isNotEmpty());
		assertThat(CENTRAL_RECEIVED_BODY.get()).isEqualTo("{\"beneficiarioId\":\"BEN001\"}");
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
		assertThat(CENTRAL_RECEIVED_BODY.get()).isNull();
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
		centralResponds(503, "{\"erro\":\"Central indisponível no momento (simulado). Tente novamente.\"}");

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
		CENTRAL_BEHAVIOR.set(HttpExchange::close);

		var result = mockMvc.perform(createAuthorization("{\"beneficiaryId\":\"BEN001\"}"));

		result.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("CENTRAL_CONVENIOS_UNAVAILABLE"));
		assertThat(authorizationRepository.count()).isZero();
	}

	@Test
	void returnsUnavailableWhenCentralTimesOut() throws Exception {
		CENTRAL_BEHAVIOR.set(exchange -> {
			sleep(1_500);
			respond(exchange, 201, "{\"autorizacaoId\":\"late\"}");
		});

		var result = mockMvc.perform(createAuthorization("{\"beneficiaryId\":\"BEN001\"}"));

		result.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("CENTRAL_CONVENIOS_UNAVAILABLE"));
		assertThat(authorizationRepository.count()).isZero();
	}

	@Test
	void returnsBadGatewayWhenCentralAnswersUnexpected4xx() throws Exception {
		centralResponds(404, "{\"erro\":\"Rota não encontrada\"}");

		var result = mockMvc.perform(createAuthorization("{\"beneficiaryId\":\"BEN001\"}"));

		result.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.code").value("CENTRAL_CONVENIOS_BAD_RESPONSE"));
		assertThat(authorizationRepository.count()).isZero();
	}

	@Test
	void returnsBadGatewayWhenCentralAnswersWithoutAuthorizationId() throws Exception {
		centralResponds(201, "{}");

		var result = mockMvc.perform(createAuthorization("{\"beneficiaryId\":\"BEN001\"}"));

		result.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.code").value("CENTRAL_CONVENIOS_BAD_RESPONSE"));
		assertThat(authorizationRepository.count()).isZero();
	}

	private static org.springframework.test.web.servlet.RequestBuilder createAuthorization(String body) {
		return post("/api/authorizations").contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static void centralResponds(int status, String body) {
		CENTRAL_BEHAVIOR.set(exchange -> respond(exchange, status, body));
	}

	private static void respond(HttpExchange exchange, int status, String body) throws IOException {
		var bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.close();
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static HttpServer startCentral() {
		try {
			var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			server.createContext("/", exchange -> {
				CENTRAL_RECEIVED_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
				CENTRAL_BEHAVIOR.get().handle(exchange);
			});
			server.start();
			return server;
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

}
