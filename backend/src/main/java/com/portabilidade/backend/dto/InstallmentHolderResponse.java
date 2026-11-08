package com.portabilidade.backend.dto;

import java.util.UUID;

import com.portabilidade.backend.domain.InstallmentHolder;
import com.portabilidade.backend.domain.PortabilityOperationStatus;

public record InstallmentHolderResponse(UUID operationId, PortabilityOperationStatus status) {

	public static InstallmentHolderResponse from(InstallmentHolder holder) {
		return holder == null ? null : new InstallmentHolderResponse(holder.operationId(), holder.status());
	}

}
