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

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/portability-operations")
public class PortabilityOperationController {

	private final PortabilityOperationService portabilityOperationService;

	public PortabilityOperationController(PortabilityOperationService portabilityOperationService) {
		this.portabilityOperationService = portabilityOperationService;
	}

	@PostMapping
	@ApiResponse(responseCode = "202", description = "New operation submitted, or a FAILED one retried with the same key",
			headers = @Header(name = HttpHeaders.LOCATION, description = "URL of the operation"))
	@ApiResponse(responseCode = "200", description = "Idempotent replay: same key and payload, operation unchanged")
	@ApiResponse(responseCode = "400", description = "IDEMPOTENCY_KEY_REQUIRED: header missing or not a UUID; "
			+ "VALIDATION_ERROR: body missing, malformed or invalid")
	@ApiResponse(responseCode = "409", description = "INSTALLMENT_IN_OPERATION: installments held by another PENDING or "
			+ "CONFIRMED operation, listed in installmentIds")
	@ApiResponse(responseCode = "422", description = "AUTHORIZATION_REQUIRED: no authorization registered for the "
			+ "beneficiary; INSTALLMENT_NOT_ELIGIBLE: installments missing from the schedule or not OPEN, listed in "
			+ "installmentIds; IDEMPOTENCY_KEY_REUSED: key already used with a different payload")
	@ApiResponse(responseCode = "502", description = "CENTRAL_CONVENIOS_BAD_RESPONSE: Central answered unexpectedly; "
			+ "repeat with the same Idempotency-Key")
	@ApiResponse(responseCode = "503", description = "CENTRAL_CONVENIOS_UNAVAILABLE: Central unreachable or "
			+ "timed out; repeat with the same Idempotency-Key")
	public ResponseEntity<PortabilityOperationResponse> create(
			@Parameter(description = "UUID chosen by the client, one per intent") @RequestHeader("Idempotency-Key")
			UUID idempotencyKey,
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
	@ApiResponse(responseCode = "200", description = "The operation's current state",
			headers = @Header(name = HttpHeaders.RETRY_AFTER,
					description = "While PENDING: seconds until the next check against the Central"))
	@ApiResponse(responseCode = "400", description = "BAD_REQUEST: id is not a UUID")
	@ApiResponse(responseCode = "404", description = "PORTABILITY_OPERATION_NOT_FOUND")
	public ResponseEntity<PortabilityOperationResponse> get(@PathVariable UUID id) {
		var operation = portabilityOperationService.get(id);
		var response = ResponseEntity.ok();
		if (operation.isPending()) {
			response.header(HttpHeaders.RETRY_AFTER, String.valueOf(operation.secondsUntilNextCheck()));
		}
		return response.body(PortabilityOperationResponse.from(operation));
	}

}
