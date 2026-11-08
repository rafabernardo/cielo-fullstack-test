package com.portabilidade.backend.integration.centralconvenios;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
	private static final Map<String, HttpHandler> ROUTES = new ConcurrentHashMap<>();
	private static final List<ReceivedRequest> REQUESTS = new CopyOnWriteArrayList<>();
	private static final HttpServer SERVER = start();

	private CentralConveniosStub() {
	}

	public static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("central-convenios.base-url", () -> "http://localhost:" + SERVER.getAddress().getPort());
		registry.add("central-convenios.read-timeout", READ_TIMEOUT::toString);
	}

	public static void reset() {
		ROUTES.clear();
		REQUESTS.clear();
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

	/** Answers only requests to {@code method path}; other requests fall back to the default behavior. */
	public static void respondTo(String method, String path, int status, String body) {
		ROUTES.put(method + " " + path, exchange -> send(exchange, status, body));
	}

	/** Like {@link #respondTo(String, String, int, String)}, also sending {@code Retry-After: seconds}. */
	public static void respondToWithRetryAfter(String method, String path, int status, String body, long seconds) {
		ROUTES.put(method + " " + path, exchange -> {
			exchange.getResponseHeaders().add("Retry-After", String.valueOf(seconds));
			send(exchange, status, body);
		});
	}

	public static void respondToAfter(String method, String path, Duration delay, int status, String body) {
		ROUTES.put(method + " " + path, exchange -> {
			sleep(delay);
			send(exchange, status, body);
		});
	}

	public static void dropConnection() {
		BEHAVIOR.set(HttpExchange::close);
	}

	/** The last request the Central received, or {@code null} when it was not called. */
	public static ReceivedRequest lastRequest() {
		return REQUESTS.isEmpty() ? null : REQUESTS.getLast();
	}

	/** Every request the Central received since the last reset, in arrival order. */
	public static List<ReceivedRequest> requests() {
		return List.copyOf(REQUESTS);
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
				REQUESTS.add(new ReceivedRequest(exchange.getRequestMethod(), path, body));
				ROUTES.getOrDefault(exchange.getRequestMethod() + " " + path, BEHAVIOR.get()).handle(exchange);
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
