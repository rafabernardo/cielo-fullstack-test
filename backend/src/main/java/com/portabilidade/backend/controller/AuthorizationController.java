package com.portabilidade.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.portabilidade.backend.dto.AuthorizationResponse;
import com.portabilidade.backend.dto.CreateAuthorizationRequest;
import com.portabilidade.backend.service.AuthorizationService;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/authorizations")
public class AuthorizationController {

	private final AuthorizationService authorizationService;

	public AuthorizationController(AuthorizationService authorizationService) {
		this.authorizationService = authorizationService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@ApiResponse(responseCode = "201", description = "Authorization registered at the Central and stored")
	@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: body missing, malformed or invalid")
	@ApiResponse(responseCode = "502", description = "CENTRAL_CONVENIOS_BAD_RESPONSE: Central answered unexpectedly")
	@ApiResponse(responseCode = "503", description = "CENTRAL_CONVENIOS_UNAVAILABLE: Central unreachable or timed out")
	public AuthorizationResponse create(@Valid @RequestBody CreateAuthorizationRequest request) {
		return AuthorizationResponse.from(authorizationService.create(request.beneficiaryId()));
	}

}
