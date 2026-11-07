package com.portabilidade.backend.integration.centralconvenios;

import com.fasterxml.jackson.annotation.JsonProperty;

record CentralAuthorizationResponse(@JsonProperty("autorizacaoId") String authorizationId) {
}
