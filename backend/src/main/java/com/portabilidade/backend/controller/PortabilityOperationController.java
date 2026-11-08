package com.portabilidade.backend.controller;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.portabilidade.backend.dto.CreatePortabilityOperationRequest;
import com.portabilidade.backend.dto.PortabilityOperationResponse;
import com.portabilidade.backend.service.PortabilityOperationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/portability-operations")
public class PortabilityOperationController {

	private final PortabilityOperationService portabilityOperationService;
	private final PortabilityOperationStream portabilityOperationStream;

	public PortabilityOperationController(PortabilityOperationService portabilityOperationService,
			PortabilityOperationStream portabilityOperationStream) {
		this.portabilityOperationService = portabilityOperationService;
		this.portabilityOperationStream = portabilityOperationStream;
	}

	@PostMapping
	public ResponseEntity<PortabilityOperationResponse> create(@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			@Valid @RequestBody CreatePortabilityOperationRequest request) {
		var creation = portabilityOperationService.create(idempotencyKey, request.beneficiaryId(),
				request.installmentIds(), request.destinationAccount().toDomain());
		var body = PortabilityOperationResponse.from(creation.operation());
		if (!creation.submitted()) {
			return ResponseEntity.ok(body);
		}
		var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(body.id()).toUri();
		return ResponseEntity.accepted().location(location).body(body);
	}

	@GetMapping("/{id}")
	public ResponseEntity<PortabilityOperationResponse> get(@PathVariable UUID id) {
		var operation = portabilityOperationService.get(id);
		var response = ResponseEntity.ok();
		if (operation.isPending()) {
			response.header(HttpHeaders.RETRY_AFTER, String.valueOf(operation.secondsUntilNextCheck()));
		}
		return response.body(PortabilityOperationResponse.from(operation));
	}

	@GetMapping(path = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter events(@PathVariable UUID id) {
		return portabilityOperationStream.subscribe(id);
	}

}
