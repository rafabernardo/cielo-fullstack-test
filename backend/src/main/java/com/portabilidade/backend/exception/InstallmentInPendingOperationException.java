package com.portabilidade.backend.exception;

import java.util.List;
import java.util.UUID;

public class InstallmentInPendingOperationException extends InstallmentInOperationException {

	public InstallmentInPendingOperationException(List<String> installmentIds, List<UUID> operationIds) {
		super("Installments are already in a pending portability operation.", installmentIds, operationIds);
	}

}
