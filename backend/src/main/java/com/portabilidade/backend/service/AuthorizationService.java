package com.portabilidade.backend.service;

import org.springframework.stereotype.Service;

import com.portabilidade.backend.domain.Authorization;
import com.portabilidade.backend.integration.centralconvenios.CentralConveniosGateway;
import com.portabilidade.backend.repository.AuthorizationRepository;

@Service
public class AuthorizationService {

	private final CentralConveniosGateway centralConveniosGateway;
	private final AuthorizationRepository authorizationRepository;

	public AuthorizationService(CentralConveniosGateway centralConveniosGateway,
			AuthorizationRepository authorizationRepository) {
		this.centralConveniosGateway = centralConveniosGateway;
		this.authorizationRepository = authorizationRepository;
	}

	public Authorization create(String beneficiaryId) {
		var centralAuthorizationId = centralConveniosGateway.createAuthorization(beneficiaryId);
		return authorizationRepository.save(new Authorization(beneficiaryId, centralAuthorizationId));
	}

}
