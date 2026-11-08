package com.portabilidade.backend.controller;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.portabilidade.backend.dto.CreatePortabilityOperationRequest;
import com.portabilidade.backend.dto.PortabilityOperationResponse;
import com.portabilidade.backend.service.PortabilityOperationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/portability-operations")
public class PortabilityOperationController {

	private final PortabilityOperationService portabilityOperationService;

	public PortabilityOperationController(PortabilityOperationService portabilityOperationService) {
		this.portabilityOperationService = portabilityOperationService;
	}

	@PostMapping
	public ResponseEntity<PortabilityOperationResponse> create(@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			@Valid @RequestBody CreatePortabilityOperationRequest request) {
		var creation = portabilityOperationService.create(idempotencyKey, request.beneficiaryId(),
				request.installmentIds(), request.destinationAccount().toDomain());
		var body = PortabilityOperationResponse.from(creation.operation());
		if (!creation.wasSubmitted()) {
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

}
