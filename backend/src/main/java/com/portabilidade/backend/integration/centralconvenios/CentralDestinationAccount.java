package com.portabilidade.backend.integration.centralconvenios;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CentralDestinationAccount(
		@JsonProperty("banco") String bankCode,
		@JsonProperty("agencia") String branch,
		@JsonProperty("conta") String accountNumber) {
}
