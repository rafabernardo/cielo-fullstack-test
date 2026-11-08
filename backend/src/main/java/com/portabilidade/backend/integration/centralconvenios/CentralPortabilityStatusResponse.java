package com.portabilidade.backend.integration.centralconvenios;

import com.fasterxml.jackson.annotation.JsonProperty;

record CentralPortabilityStatusResponse(
		@JsonProperty("status") CentralPortabilityStatus status,
		@JsonProperty("motivoRejeicao") String rejectionReason) {
}
