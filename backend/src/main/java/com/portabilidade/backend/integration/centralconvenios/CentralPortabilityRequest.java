package com.portabilidade.backend.integration.centralconvenios;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CentralPortabilityRequest(
		@JsonProperty("beneficiarioId") String beneficiaryId,
		@JsonProperty("parcelaIds") List<String> installmentIds,
		@JsonProperty("contaDestino") CentralDestinationAccount destinationAccount) {
}
