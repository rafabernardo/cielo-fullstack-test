package com.portabilidade.backend.controller;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.MediaType;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.portabilidade.backend.domain.PortabilityOperation;
import com.portabilidade.backend.domain.PortabilityOperationStatusChanged;
import com.portabilidade.backend.dto.PortabilityOperationResponse;
import com.portabilidade.backend.service.PortabilityOperationService;

/**
 * Pushes an operation's status to its SSE subscribers: once on subscribe, then after every committed change.
 * The stream completes once the operation leaves PENDING.
 */
// ponytail: subscribers live in this instance's memory; with several backend instances, fan out through a broker.
@RestController
@RequestMapping("/api/portability-operations")
public class PortabilityOperationEventsController {

	private static final String STATUS_EVENT = "status";

	private final PortabilityOperationService portabilityOperationService;
	private final Map<UUID, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();

	public PortabilityOperationEventsController(PortabilityOperationService portabilityOperationService) {
		this.portabilityOperationService = portabilityOperationService;
	}

	@GetMapping(path = "/{operationId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter subscribe(@PathVariable UUID operationId) {
		portabilityOperationService.get(operationId); // 404 before any stream is opened
		var emitter = new SseEmitter(); // timeout: spring.mvc.async.request-timeout
		var operationSubscribers = subscribers.computeIfAbsent(operationId, id -> ConcurrentHashMap.newKeySet());
		operationSubscribers.add(emitter);
		emitter.onCompletion(() -> unsubscribe(operationId, emitter));
		emitter.onTimeout(emitter::complete);
		emitter.onError(error -> unsubscribe(operationId, emitter));
		// Registered before reading, so a change committed meanwhile is pushed again rather than lost.
		send(emitter, portabilityOperationService.get(operationId));
		return emitter;
	}

	@TransactionalEventListener
	void onStatusChanged(PortabilityOperationStatusChanged event) {
		var operationSubscribers = subscribers.get(event.operationId());
		if (operationSubscribers == null || operationSubscribers.isEmpty()) {
			return;
		}
		var operation = portabilityOperationService.get(event.operationId());
		operationSubscribers.forEach(emitter -> send(emitter, operation));
	}

	private void send(SseEmitter emitter, PortabilityOperation operation) {
		try {
			emitter.send(SseEmitter.event()
					.name(STATUS_EVENT)
					.data(PortabilityOperationResponse.from(operation), MediaType.APPLICATION_JSON));
			if (!operation.isPending()) {
				emitter.complete();
			}
		} catch (IOException | IllegalStateException e) {
			emitter.completeWithError(e); // client went away or stream already completed
		}
	}

	private void unsubscribe(UUID operationId, SseEmitter emitter) {
		subscribers.computeIfPresent(operationId, (id, emitters) -> {
			emitters.remove(emitter);
			return emitters.isEmpty() ? null : emitters;
		});
	}

}
