package com.portabilidade.backend.exception;

import java.util.List;

public class InstallmentInOperationException extends RuntimeException {

	private final List<String> installmentIds;

	public InstallmentInOperationException(List<String> installmentIds) {
		super("Installments are already in a pending or confirmed portability operation.");
		this.installmentIds = List.copyOf(installmentIds);
	}

	public List<String> getInstallmentIds() {
		return installmentIds;
	}

}
