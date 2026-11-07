package com.portabilidade.backend.dto;

import java.time.Instant;
import java.util.UUID;

import com.portabilidade.backend.domain.Authorization;

public record AuthorizationResponse(UUID id, String beneficiaryId, String centralAuthorizationId, Instant createdAt) {

	public static AuthorizationResponse from(Authorization authorization) {
		return new AuthorizationResponse(authorization.getId(), authorization.getBeneficiaryId(),
				authorization.getCentralAuthorizationId(), authorization.getCreatedAt());
	}

}
