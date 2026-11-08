package com.portabilidade.backend.exception;

import java.util.List;

public class InstallmentNotEligibleException extends RuntimeException {

	private final List<String> installmentIds;

	public InstallmentNotEligibleException(List<String> installmentIds) {
		super("Installments are not in the beneficiary's schedule or are not eligible for portability.");
		this.installmentIds = List.copyOf(installmentIds);
	}

	public List<String> getInstallmentIds() {
		return installmentIds;
	}

}
