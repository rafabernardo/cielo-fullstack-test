package com.portabilidade.backend.exception;

import java.util.List;
import java.util.UUID;

public class InstallmentInOperationException extends RuntimeException {

	private final List<String> installmentIds;
	private final List<UUID> operationIds;

	public InstallmentInOperationException(List<String> installmentIds, List<UUID> operationIds) {
		super("Installments are already in a pending or confirmed portability operation.");
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
