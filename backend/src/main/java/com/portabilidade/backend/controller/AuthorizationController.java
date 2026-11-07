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
	public AuthorizationResponse create(@Valid @RequestBody CreateAuthorizationRequest request) {
		return AuthorizationResponse.from(authorizationService.create(request.beneficiaryId()));
	}

}
