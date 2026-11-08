package com.portabilidade.backend.exception;

import java.util.List;
import java.util.UUID;

// Installments already held by another operation; subclasses say whether the holder is pending or confirmed.
public abstract class InstallmentInOperationException extends RuntimeException {

	private final List<String> installmentIds;
	private final List<UUID> operationIds;

	protected InstallmentInOperationException(String message, List<String> installmentIds, List<UUID> operationIds) {
		super(message);
		this.installmentIds = List.copyOf(installmentIds);
		this.operationIds = List.copyOf(operationIds);
	}

	public List<String> getInstallmentIds() {
		return installmentIds;
	}

	public List<UUID> getOperationIds() {
		return operationIds;
	}

}
