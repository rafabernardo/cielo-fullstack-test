package com.portabilidade.backend.exception;

import java.util.List;
import java.util.UUID;

public class InstallmentInConfirmedOperationException extends InstallmentInOperationException {

	public InstallmentInConfirmedOperationException(List<String> installmentIds, List<UUID> operationIds) {
		super("Installments are already in a confirmed portability operation.", installmentIds, operationIds);
	}

}
