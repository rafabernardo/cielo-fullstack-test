package com.portabilidade.backend.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

// A future payroll deduction of a beneficiary, as read from the Central's schedule. Not persisted.
public record Installment(String installmentId, String agreementId, BigDecimal amount, LocalDate deductionDate,
		InstallmentStatus status, boolean inActiveOperation) {

	public boolean isOpen() {
		return status == InstallmentStatus.OPEN;
	}

	// Whether it can be selected for portability.
	public boolean isEligible() {
		return isOpen() && !inActiveOperation;
	}

}
