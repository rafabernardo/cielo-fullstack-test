package com.portabilidade.backend.integration.centralconvenios;

import com.fasterxml.jackson.annotation.JsonProperty;

record CentralAuthorizationRequest(@JsonProperty("beneficiarioId") String beneficiaryId) {
}
