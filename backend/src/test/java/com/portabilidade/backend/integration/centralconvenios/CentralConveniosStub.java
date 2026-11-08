package com.portabilidade.backend.integration.centralconvenios;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.test.context.DynamicPropertyRegistry;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Fake Central de Convênios over real HTTP, shared by all API tests. One server per JVM, so every test class
 * registers the same properties and reuses the same Spring context.
 */
public final class CentralConveniosStub {

	public record ReceivedRequest(String method, String path, String body) {
	}

	public static final Duration READ_TIMEOUT = Duration.ofMillis(500);

	private static final AtomicReference<HttpHandler> BEHAVIOR = new AtomicReference<>();
	private static final AtomicReference<ReceivedRequest> LAST_REQUEST = new AtomicReference<>();
	private static final HttpServer SERVER = start();

	private CentralConveniosStub() {
	}

	public static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("central-convenios.base-url", () -> "http://localhost:" + SERVER.getAddress().getPort());
		registry.add("central-convenios.read-timeout", READ_TIMEOUT::toString);
	}

	public static void reset() {
		LAST_REQUEST.set(null);
		respond(500, "{\"erro\":\"Stub not configured for this test\"}");
	}

	public static void respond(int status, String body) {
		BEHAVIOR.set(exchange -> send(exchange, status, body));
	}

	public static void respondAfter(Duration delay, int status, String body) {
		BEHAVIOR.set(exchange -> {
			sleep(delay);
			send(exchange, status, body);
		});
	}

	public static void dropConnection() {
		BEHAVIOR.set(HttpExchange::close);
	}

	/** The last request the Central received, or {@code null} when it was not called. */
	public static ReceivedRequest lastRequest() {
		return LAST_REQUEST.get();
	}

	private static void send(HttpExchange exchange, int status, String body) throws IOException {
		var bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		// HttpServer treats length 0 as chunked; -1 means "no body".
		exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.close();
	}

	private static void sleep(Duration delay) {
		try {
			Thread.sleep(delay);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static HttpServer start() {
		try {
			var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			server.createContext("/", exchange -> {
				var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
				var path = exchange.getRequestURI().getPath();
				LAST_REQUEST.set(new ReceivedRequest(exchange.getRequestMethod(), path, body));
				BEHAVIOR.get().handle(exchange);
			});
			// Concurrent handling, so a slow "timeout" response never delays requests of the following tests.
			server.setExecutor(Executors.newCachedThreadPool());
			server.start();
			return server;
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

}
