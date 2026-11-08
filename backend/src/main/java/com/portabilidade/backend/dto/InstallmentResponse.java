package com.portabilidade.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.portabilidade.backend.domain.Installment;
import com.portabilidade.backend.domain.InstallmentStatus;

public record InstallmentResponse(String installmentId, String agreementId, BigDecimal amount,
		LocalDate deductionDate, InstallmentStatus status, boolean eligible, InstallmentHolderResponse heldBy) {

	public static InstallmentResponse from(Installment installment) {
		return new InstallmentResponse(installment.installmentId(), installment.agreementId(), installment.amount(),
				installment.deductionDate(), installment.status(), installment.isEligible(),
				InstallmentHolderResponse.from(installment.heldBy()));
	}

}
