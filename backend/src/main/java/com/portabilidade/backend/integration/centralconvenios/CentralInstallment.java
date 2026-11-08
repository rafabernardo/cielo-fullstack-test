package com.portabilidade.backend.integration.centralconvenios;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CentralInstallment(
		@JsonProperty("parcelaId") String installmentId,
		@JsonProperty("convenioId") String agreementId,
		@JsonProperty("valor") BigDecimal amount,
		@JsonProperty("dataDesconto") LocalDate deductionDate,
		@JsonProperty("status") CentralInstallmentStatus status) {

	// Jackson accepts missing or null fields; the gateway uses this to reject such installments as a bad response.
	boolean isComplete() {
		return installmentId != null && agreementId != null && amount != null && deductionDate != null
				&& status != null;
	}

}
