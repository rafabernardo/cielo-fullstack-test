package com.portabilidade.backend.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

// A future payroll deduction of a beneficiary, as read from the Central's schedule. Not persisted.
public record Installment(String installmentId, String agreementId, BigDecimal amount, LocalDate deductionDate,
		InstallmentStatus status) {

	// Whether it can be selected for portability. Next: also exclude installments in a pending/confirmed operation.
	public boolean isEligible() {
		return status == InstallmentStatus.OPEN;
	}

}
