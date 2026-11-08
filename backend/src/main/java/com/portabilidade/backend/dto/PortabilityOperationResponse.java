package com.portabilidade.backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.portabilidade.backend.domain.PortabilityOperation;
import com.portabilidade.backend.domain.PortabilityOperationStatus;

public record PortabilityOperationResponse(UUID id, String beneficiaryId, List<String> installmentIds,
		DestinationAccountResponse destinationAccount, PortabilityOperationStatus status, String rejectionReason,
		Instant createdAt, Instant updatedAt) {

	public static PortabilityOperationResponse from(PortabilityOperation operation) {
		return new PortabilityOperationResponse(operation.getId(), operation.getBeneficiaryId(),
				operation.getInstallmentIds(), DestinationAccountResponse.from(operation.getDestinationAccount()),
				operation.getStatus(), operation.getRejectionReason(), operation.getCreatedAt(),
				operation.getUpdatedAt());
	}

}
