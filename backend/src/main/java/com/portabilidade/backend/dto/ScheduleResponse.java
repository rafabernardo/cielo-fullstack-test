package com.portabilidade.backend.dto;

import java.util.List;

import com.portabilidade.backend.domain.Installment;

public record ScheduleResponse(String beneficiaryId, List<InstallmentResponse> installments) {

	public static ScheduleResponse from(String beneficiaryId, List<Installment> installments) {
		return new ScheduleResponse(beneficiaryId, installments.stream().map(InstallmentResponse::from).toList());
	}

}
